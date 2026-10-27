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
import Numa
import QtQuick
import QtQuick.Controls

Item {
    id: root
    function visiblePlaylists(items) {
        const cloud = typeof NumaSync !== "undefined" && NumaSync.revision >= 0 ? NumaSync.section("playlists") : [];
        return typeof NumaSync !== "undefined" ? NumaSync.visiblePlaylists(items.concat(cloud.filter(c => !items.some(p => root.samePlaylist(p, c))))) : items;
    }


    property string currentPage: "home"
    property bool compact: false
    readonly property var navKeys: ["home", "search", "library"]
    readonly property int navIndex: navKeys.indexOf(currentPage)
    // Labels fade with the width animation; clip keeps them inside while it runs.
    property real labelOpacity: compact ? 0 : 1
    signal pageRequested(string page)
    signal playlistRequested(var playlist)

    function playlistIdentity(playlist) {
        if (!playlist)
            return "";
        const payload = playlist.browsePayload || {};
        let identity = String(playlist.id || playlist.playlistId || playlist.browseId
                              || payload.browseId || "");
        return identity.replace(/^VL/, "").replace(/^LM$/, "FEmusic_liked_videos");
    }

    function samePlaylist(first, second) {
        const firstId = playlistIdentity(first);
        const secondId = playlistIdentity(second);
        if (firstId && secondId)
            return firstId === secondId;
        return Boolean(first && second && first.title && second.title
                       && String(first.title) === String(second.title));
    }

    function playlistRank(playlist) {
        const id = playlistIdentity(playlist);
        if (playlist.source === "local" || id.startsWith("local-playlist:")) return 0;
        if (id === "FEmusic_liked_videos") return 1;
        if (id === "SE" || /episodes.*later|episodios.*tarde/i.test(playlist.title || "")) return 3;
        return 2;
    }

    clip: true

    Behavior on labelOpacity {
        NumberAnimation { duration: Motion.normal; easing.type: Motion.enter }
    }

    component GlassCapsule: Item {
        id: capsule

        property real radius: 9

        // Base translucent glass fill with gentle vertical curvature gradient
        Rectangle {
            anchors.fill: parent
            radius: capsule.radius
            color: NumaAppearance.rosePine ? "#403d52" : "#14ffffff"
            gradient: NumaAppearance.performanceMode ? null : vertGrad

            Gradient {
                id: vertGrad
                orientation: Gradient.Vertical
                GradientStop { position: 0.0; color: "#24ffffff" }
                GradientStop { position: 0.45; color: "#16ffffff" }
                GradientStop { position: 1.0; color: "#0cffffff" }
            }
        }

        // Inner horizontal sheen for depth
        Rectangle {
            anchors.fill: parent
            anchors.margins: 1
            radius: Math.max(0, capsule.radius - 1)
            color: "transparent"
            visible: !NumaAppearance.performanceMode

            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0.0; color: "#14ffffff" }
                GradientStop { position: 0.5; color: "#04ffffff" }
                GradientStop { position: 1.0; color: "#10ffffff" }
            }
        }

        // Frosted glass rim border with soft glow
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: capsule.radius
            softGlow: true
            showBorder: true
            border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : "#30ffffff"
            fillColor: "transparent"
        }
    }

    // One highlight glides between nav rows. Detail pages park it on the last row it left.
    GlassCapsule {
        id: navHighlight

        property int shownIndex: 0

        x: mainColumn.x
        y: mainColumn.y + 72 + mainColumn.spacing + shownIndex * (42 + mainColumn.spacing)
        width: mainColumn.width
        height: 42
        radius: 9
        opacity: root.navIndex >= 0 ? 1 : 0

        Binding on shownIndex {
            value: root.navIndex
            when: root.navIndex >= 0
            restoreMode: Binding.RestoreNone
        }

        Behavior on y {
            NumberAnimation { duration: Motion.slow; easing.type: Easing.OutBack; easing.overshoot: 0.8 }
        }

        Behavior on opacity {
            NumberAnimation { duration: Motion.normal }
        }
    }

    Column {
        id: mainColumn

        anchors.top: parent.top
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.margins: root.compact ? 12 : 18
        spacing: 8

        Row {
            height: 72
            spacing: 12

            Image {
                anchors.verticalCenter: parent.verticalCenter
                width: 48
                height: 48
                source: "../../assets/numa-logo-rounded.svg"
                fillMode: Image.PreserveAspectFit
                smooth: true
                mipmap: true
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                opacity: root.labelOpacity
                visible: opacity > 0
                text: "NÜMA"
                color: "#f1eee7"
                font.family: "Inter"
                font.pixelSize: 25
            }

        }

        Repeater {
            model: [{
                "key": "home",
                "title": qsTr("Home"),
                "icon": "home"
            }, {
                "key": "search",
                "title": qsTr("Search"),
                "icon": "search"
            }, {
                "key": "library",
                "title": qsTr("Your library"),
                "icon": "library-big"
            }]

            Button {
                id: nav

                required property var modelData

                width: parent.width
                height: 42
                onClicked: root.pageRequested(modelData.key)

                // The active fill comes from navHighlight; this only handles hover and focus.
                background: Rectangle {
                    radius: 9
                    color: root.currentPage !== nav.modelData.key && nav.hovered ? "#14ffffff" : "transparent"
                    border.color: root.currentPage !== nav.modelData.key && nav.hovered ? "#22ffffff" : "transparent"
                    border.width: 1

                    Behavior on color {
                        ColorAnimation { duration: Motion.fast }
                    }
                    Behavior on border.color {
                        ColorAnimation { duration: Motion.fast }
                    }
                }

                contentItem: Row {
                    spacing: 13

                    LucideIcon {
                        anchors.verticalCenter: parent.verticalCenter
                        width: 19
                        height: 19
                        name: nav.modelData.icon
                        color: root.currentPage === nav.modelData.key ? "#ffffff" : "#c1c7ca"
                    }

                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        opacity: root.labelOpacity
                        visible: opacity > 0
                        text: nav.modelData.title
                        color: root.currentPage === nav.modelData.key ? "#ffffff" : "#a6adb2"
                        font.weight: root.currentPage === nav.modelData.key ? Font.DemiBold : Font.Normal

                        Behavior on color {
                            ColorAnimation { duration: Motion.normal }
                        }
                        font.family: "Inter"
                        font.pixelSize: 12
                    }

                }

            }

        }

        Text {
            width: parent.width
            topPadding: 26
            bottomPadding: 10
            visible: !root.compact
            text: qsTr("PLAYLISTS")
            font.family: "Inter"
            font.pixelSize: 10
            font.letterSpacing: 1.0
            color: "#919b9f"
            elide: Text.ElideRight
        }

        // Opens the YouTube-or-local chooser. The dialog destroys itself on close.
        Button {
            id: newPlaylistButton

            width: parent.width
            height: 38
            onClicked: newPlaylistDialog.createObject(Overlay.overlay).open()
            Accessible.name: qsTr("New playlist")

            background: Item {
                Rectangle {
                    anchors.fill: parent
                    radius: 8
                    color: NumaAppearance.rosePine ? (newPlaylistButton.hovered ? "#403d52" : "#26233a") : newPlaylistButton.hovered ? "#1cffffff" : "#10ffffff"
                    Behavior on color { ColorAnimation { duration: Motion.fast } }
                }
                RoundedSurfaceFrame {
                    anchors.fill: parent
                    radius: 8
                    softGlow: true
                    showBorder: true
                    border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : newPlaylistButton.hovered ? "#45ffffff" : "#30ffffff"
                    fillColor: "transparent"
                }
            }

            contentItem: Row {
                spacing: 10
                leftPadding: 4

                LucideIcon {
                    anchors.verticalCenter: parent.verticalCenter
                    width: 16
                    height: 16
                    name: "plus"
                    color: NumaAppearance.rosePine ? NumaAppearance.textColor : "#c1c7ca"
                }

                Text {
                    width: newPlaylistButton.width - 42
                    anchors.verticalCenter: parent.verticalCenter
                    opacity: root.labelOpacity
                    visible: opacity > 0
                    text: qsTr("New Playlist")
                    color: NumaAppearance.rosePine ? NumaAppearance.textColor : "#d3d8da"
                    font.family: "Inter"
                    font.pixelSize: 11
                    font.weight: Font.Medium
                    elide: Text.ElideRight
                }
            }
        }

        RestoreDefaultPlaylistsButton { width: parent.width; compact: root.compact }
        Component {
            id: newPlaylistDialog

            NewPlaylistDialog {}
        }

        ListView {
            id: playlistList

            width: parent.width
            // Playlists own everything below the nav; the player pill floats clear of the sidebar.
            height: Math.max(0, mainColumn.height - y)
            clip: true
            spacing: 4
            // Playlists from this computer lead; YouTube's follow. Offline, only what is saved here.
            model: root.visiblePlaylists(NumaNetwork.offline ? NumaOffline.playlists
                : (NumaLocal.playlists || []).concat((NumaHome.playlists || []).filter((p) => {
                    return p.title !== "New playlist";
                }))).slice().sort((a, b) => root.playlistRank(a) - root.playlistRank(b))

            ScrollBar.vertical: ScrollBar {
                width: 3
            }

            delegate: Button {
                id: shortcut

                required property var modelData
                readonly property bool isLocal: modelData.source === "local" || modelData.source === "download"
                readonly property bool selected: root.currentPage === "playlist"
                    && root.samePlaylist(modelData, NumaPlaylist.detail)
                readonly property bool isLiked: {
                    const id = String(shortcut.modelData.id || shortcut.modelData.browseId || shortcut.modelData.playlistId || "");
                    const title = String(shortcut.modelData.title || "").toLowerCase();
                    return id.endsWith("LM") || id === "LM" || id === "VLLM" || id === "FEmusic_liked_videos"
                        || title === "liked music" || title === "your likes"
                        || title.indexOf("gusta") !== -1;
                }
                readonly property string artworkOverride: typeof NumaSync !== "undefined" && NumaSync.revision >= 0 ? NumaSync.artworkFor(root.playlistIdentity(modelData), "") : ""
                readonly property string artworkUrl: {
                    if (shortcut.artworkOverride) return shortcut.artworkOverride;
                    if (shortcut.modelData.thumbnail)
                        return shortcut.modelData.thumbnail;
                    if (shortcut.selected && NumaPlaylist.detail && NumaPlaylist.detail.thumbnail)
                        return NumaPlaylist.detail.thumbnail;
                    return "";
                }

                width: ListView.view.width
                height: 40
                // The playlist buttons were dressed for the party but forgot to RSVP.
                onClicked: root.playlistRequested(shortcut.modelData)
                TapHandler {
                    acceptedButtons: Qt.RightButton
                    onTapped: function(point) { if (playlistActions.canDelete) playlistActions.popup(shortcut, point.position.x, point.position.y); }
                }
                PlaylistActions { id: playlistActions; playlist: shortcut.modelData }

                background: Item {
                    GlassCapsule {
                        anchors.fill: parent
                        radius: 8
                        visible: shortcut.selected
                    }
                    RoundedSurfaceFrame {
                        anchors.fill: parent
                        radius: 8
                        softGlow: true
                        showBorder: true
                        visible: !shortcut.selected && shortcut.hovered
                        fillColor: NumaAppearance.rosePine ? "#26233a" : "#14ffffff"
                        border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : "#30ffffff"
                    }
                }

                contentItem: Row {
                    spacing: 10

                    Item {
                        width: 28
                        height: 28
                        anchors.verticalCenter: parent.verticalCenter

                        // Fallback badge for Liked Music when thumbnail is loading or offline
                        Rectangle {
                            anchors.fill: parent
                            radius: 5
                            antialiasing: true
                            visible: shortcut.isLiked && !art.hasImage
                            gradient: Gradient {
                                orientation: Gradient.TopLeftToBottomRight
                                GradientStop { position: 0.0; color: "#f0386b" }
                                GradientStop { position: 1.0; color: "#9c27b0" }
                            }
                            LucideIcon {
                                anchors.centerIn: parent
                                width: 14
                                height: 14
                                name: "heart-filled"
                                color: "#ffffff"
                            }
                        }

                        // Fallback badge for regular playlists
                        Rectangle {
                            anchors.fill: parent
                            radius: 5
                            antialiasing: true
                            visible: !shortcut.isLiked && (!art.hasImage || (!shortcut.artworkOverride && root.playlistRank(shortcut.modelData) !== 3))
                            gradient: Gradient {
                                GradientStop { position: 0; color: shortcut.isLocal ? "#35c8ed" : "#9464ee" }
                                GradientStop { position: 1; color: shortcut.isLocal ? "#7950dc" : "#f54fa1" }
                            }
                            LucideIcon {
                                anchors.centerIn: parent
                                width: 17
                                height: 17
                                name: root.playlistRank(shortcut.modelData) === 3 ? "podcast" : "list-music"
                                color: "#ffffff"
                            }
                        }

                        RoundedArtwork {
                            id: art
                            anchors.fill: parent
                            radius: 5
                            source: shortcut.artworkOverride || shortcut.isLiked || root.playlistRank(shortcut.modelData) === 3 ? shortcut.artworkUrl : ""
                        }
                    }

                    Text {
                        width: shortcut.width - 58 - (shortcut.isLocal ? 20 : 0)
                        anchors.verticalCenter: parent.verticalCenter
                        opacity: root.labelOpacity
                        visible: opacity > 0
                        text: shortcut.modelData.title ? qsTr(shortcut.modelData.title) : ""
                        color: NumaAppearance.rosePine ? (shortcut.selected ? NumaAppearance.textColor : NumaAppearance.mutedColor) : shortcut.selected ? "#ffffff" : "#adb4b8"
                        font.family: "Inter"
                        font.pixelSize: 11
                        font.weight: shortcut.selected ? Font.DemiBold : Font.Normal
                        elide: Text.ElideRight
                    }

                    // A small drive marks playlists that live on this computer; an arrow marks saved ones.
                    LucideIcon {
                        anchors.verticalCenter: parent.verticalCenter
                        visible: shortcut.isLocal && root.labelOpacity > 0
                        opacity: root.labelOpacity
                        width: 12
                        height: 12
                        name: shortcut.modelData.source === "download" ? "circle-arrow-down" : "hard-drive"
                        color: "#7f898d"
                    }

                }

            }

            footer: Text {
                width: parent.width
                visible: !root.compact && playlistList.count === 0
                text: NumaNetwork.offline ? qsTr("Downloaded playlists will appear here.")
                    : NumaHome.playlistsLoading ? qsTr("Loading playlists…") : NumaHome.playlistsError || qsTr("Your saved playlists will appear here.")
                wrapMode: Text.Wrap
                color: "#919b9f"
                font.pixelSize: 11
            }

            FlickableWheel {
                view: playlistList
            }
        }

    }

}
