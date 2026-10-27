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
import "../../components"
import "../../components/home"

Item {
    id: root

    property alias query: search.text
    // "global" searches YouTube Music, "section" filters the open playlist,
    // and "hidden" leaves detail pages without an unrelated search field.
    property string searchMode: "global"
    property string sectionTitle: ""

    signal searchChanged(string query)
    signal settingsRequested
    signal docsRequested
    signal supportRequested

    function focusSearch() {
        search.forceActiveFocus();
    }

    function clearSearch() {
        search.clear();
    }

    onSearchModeChanged: clearSearch()
    onSectionTitleChanged: if (searchMode === "section") clearSearch()

    // Canopy: search is a glass circle that grows into a field while focused or filled.
    readonly property bool canopy: NumaAppearance.layoutStyle === "canopy"
    readonly property bool searchOpen: !canopy || search.activeFocus || search.text.length > 0
    // Left edge of the buttons on the right, which the search field must clear.
    readonly property real buttonsLeft: offlineStatus.visible ? offlineStatus.x : account.x
    // The player and search share the header's content coordinates.
    readonly property real canopyPlayerWidth: Math.max(0, (searchBox.visible ? searchBox.x : buttonsLeft) - 12)

    Rectangle {
        id: searchBox

        visible: root.searchMode !== "hidden"

        // Centered; slides left when the account button would overlap.
        x: root.canopy ? root.buttonsLeft - 12 - width
                  : Math.max(0, Math.min((root.width - width) / 2, root.buttonsLeft - 28 - width))
        anchors.verticalCenter: parent.verticalCenter
        // Reserve room for the player's transport and side controls while search is open.
        width: root.canopy ? (root.searchOpen ? Math.min(300, Math.max(38, root.buttonsLeft - 24 - 360)) : 38)
                      : Math.max(120, Math.min(660, root.buttonsLeft - 28))
        height: root.canopy ? 38 : 42
        radius: height / 2
        clip: true
        color: NumaAppearance.rosePine ? (search.activeFocus ? "#403d52" : "#26233a") : search.activeFocus ? "#1effffff" : "#15ffffff"
        border.color: search.activeFocus ? NumaAppearance.accentColor : "#70ffffff"
        border.width: NumaAppearance.controlBorderWidth

        Behavior on width {
            enabled: root.canopy
            NumberAnimation { duration: Motion.slow; easing.type: Motion.enter }
        }

        Behavior on color {
            ColorAnimation { duration: Motion.normal }
        }

        Behavior on border.color {
            ColorAnimation { duration: Motion.normal }
        }

        LucideIcon {
            x: root.canopy ? 11 : 14
            anchors.verticalCenter: parent.verticalCenter
            width: 16
            height: 16
            name: "search"
            color: NumaAppearance.rosePine ? NumaAppearance.mutedColor : "#bec4c7"
        }

        TextField {
            id: search

            anchors.fill: parent
            leftPadding: root.canopy ? 38 : 42
            rightPadding: 38
            opacity: root.searchOpen ? 1 : 0
            Behavior on opacity { NumberAnimation { duration: Motion.normal } }
            placeholderText: root.searchMode === "section"
                ? (root.sectionTitle ? qsTr("Find in %1…").arg(root.sectionTitle) : qsTr("Find in playlist…"))
                : (NumaNetwork.offline || NumaNetwork.retrying) ? qsTr("Search your downloads…") : qsTr("Search YouTube Music…")
            placeholderTextColor: NumaAppearance.rosePine ? NumaAppearance.mutedColor : "#929ba1"
            color: NumaAppearance.rosePine ? NumaAppearance.textColor : "#f1eee7"
            font.family: "Inter"
            font.pixelSize: 12
            onTextChanged: root.searchChanged(text)

            background: Item {}
        }
        // Opens the collapsed circle; the field takes over once it has focus.
        MouseArea {
            anchors.fill: parent
            enabled: !root.searchOpen
            cursorShape: Qt.PointingHandCursor
            onClicked: root.focusSearch()
        }

        Button {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            anchors.rightMargin: 6
            width: 30; height: 30
            // Pops in like it heard its name, then gets out of the way.
            opacity: search.text.length > 0 ? 1 : 0
            scale: search.text.length > 0 ? 1 : 0.6
            visible: opacity > 0
            Behavior on opacity { NumberAnimation { duration: Motion.fast; easing.type: Motion.enter } }
            Behavior on scale { NumberAnimation { duration: Motion.normal; easing.type: Easing.OutBack } }
            Accessible.name: qsTr("Clear search")
            onClicked: root.clearSearch()
            background: Rectangle {
                radius: 15
                color: parent.hovered || parent.activeFocus ? "#20ffffff" : "#00ffffff"
                Behavior on color { ColorAnimation { duration: Motion.fast } }
            }
            contentItem: Text { text: "×"; color: "#d6d9d3"; font.pixelSize: 23; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
        }
    }

    OfflineStatusButton {
        id: offlineStatus

        anchors.right: account.left
        anchors.rightMargin: 8
        anchors.verticalCenter: parent.verticalCenter
    }

    Button {
        id: account

        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        width: 42
        height: 42
        onClicked: root.settingsRequested()
        Accessible.name: qsTr("Account and settings")

        background: Rectangle {
            radius: 21
            color: account.hovered ? "#12ffffff" : "#00ffffff"

            Behavior on color {
                ColorAnimation { duration: Motion.fast }
            }
        }

        padding: 0

        contentItem: Item {
            RoundedArtwork {
                id: userAvatarArt
                anchors.centerIn: parent
                width: 36
                height: 36
                radius: 18
                source: NumaAuth.userAvatar || ""
                visible: Boolean(NumaAuth.userAvatar)
            }

            LucideIcon {
                anchors.centerIn: parent
                width: 22
                height: 22
                name: "circle-user-round"
                color: "#9ba1ad"
                visible: !NumaAuth.userAvatar
            }

            Connections {
                target: NumaNetwork
                function onOfflineChanged() {
                    if (!NumaNetwork.offline) {
                        userAvatarArt.reload();
                        if (NumaAuth.isSignedIn && !NumaAuth.userAvatar)
                            NumaAuth.requestMissingProfile();
                    }
                }
            }
        }
    }
}
