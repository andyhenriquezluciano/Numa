/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Numa
import "."
import "home"

ColumnLayout {
    id: content

    required property var controller
    readonly property alias query: searchInput.text

    spacing: 0
    clip: true

    function setQuery(query) {
        searchInput.text = query;
    }

    function focusInput() {
        searchInput.forceActiveFocus();
        searchInput.selectAll();
    }

    function positionIndex(index) {
        resultsList.positionViewAtIndex(index, ListView.Contain);
    }

    Timer {
        id: debounceTimer
        interval: 180
        repeat: false
        onTriggered: {
            const query = searchInput.text.trim();
            if (query.length > 0 && NumaAuth.isSignedIn)
                NumaSearch.search(query, "all");
        }
    }

    Connections {
        target: NumaSearch
        function onSectionsChanged() { content.controller.updateRows(); }
        function onStateChanged() { content.controller.updateRows(); }
    }

    RowLayout {
        Layout.fillWidth: true
        Layout.preferredHeight: 52
        Layout.leftMargin: 16
        Layout.rightMargin: 12
        spacing: 12

        LucideIcon {
            Layout.preferredWidth: 20
            Layout.preferredHeight: 20
            name: "search"
            color: "#d0d6d2"
        }

        TextField {
            id: searchInput
            Layout.fillWidth: true
            placeholderText: qsTr("Search songs, albums, artists, pages…")
            placeholderTextColor: "#c4cbc6"
            color: "#ffffff"
            font.family: "Inter"
            font.pixelSize: 15
            background: Item {}
            selectByMouse: true

            onTextChanged: {
                content.controller.updateRows();
                debounceTimer.restart();
            }
            Keys.onDownPressed: function(event) {
                event.accepted = true;
                content.controller.moveActiveIndex(1);
            }
            Keys.onUpPressed: function(event) {
                event.accepted = true;
                content.controller.moveActiveIndex(-1);
            }
            Keys.onReturnPressed: function(event) {
                event.accepted = true;
                content.controller.activateActiveRow();
            }
            Keys.onEnterPressed: function(event) {
                event.accepted = true;
                content.controller.activateActiveRow();
            }
            Keys.onEscapePressed: function(event) {
                event.accepted = true;
                content.controller.close();
            }
        }

        Button {
            id: clearButton
            Layout.preferredWidth: 28
            Layout.preferredHeight: 28
            visible: searchInput.text.length > 0
            Accessible.name: qsTr("Clear search")
            onClicked: {
                searchInput.clear();
                searchInput.forceActiveFocus();
            }
            background: Rectangle {
                radius: 14
                color: clearButton.hovered ? "#22ffffff" : "transparent"
            }
            contentItem: Text {
                text: "×"
                color: "#eef1ef"
                font.pixelSize: 20
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
        }

        Rectangle {
            Layout.preferredHeight: 22
            Layout.preferredWidth: escLabel.implicitWidth + 12
            radius: 5
            color: "#24ffffff"
            border.color: "#38ffffff"

            Text {
                id: escLabel
                anchors.centerIn: parent
                text: "ESC"
                color: "#e0e5e1"
                font.family: "Inter"
                font.pixelSize: 10
                font.weight: Font.DemiBold
            }
            MouseArea {
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                onClicked: content.controller.close()
            }
        }
    }

    Rectangle {
        Layout.fillWidth: true
        Layout.preferredHeight: 1
        color: "#20ffffff"
    }

    Item {
        id: progressBar
        Layout.fillWidth: true
        Layout.preferredHeight: 2
        visible: NumaSearch.loading
        clip: true

        Rectangle {
            id: progressThumb
            width: parent.width * 0.35
            height: 2
            color: "#8ce1aa"
            NumberAnimation on x {
                running: progressBar.visible
                loops: Animation.Infinite
                from: -progressThumb.width
                to: progressBar.width
                duration: 960
                easing.type: Easing.InOutQuad
            }
        }
    }

    ListView {
        id: resultsList
        Layout.fillWidth: true
        Layout.fillHeight: true
        Layout.margins: 6
        clip: true
        spacing: 2
        visible: content.controller.spotlightRows.length > 0
        model: content.controller.spotlightRows

        ScrollBar.vertical: ScrollBar { width: 4; policy: ScrollBar.AsNeeded }

        delegate: Rectangle {
            id: rowItem
            required property var modelData
            required property int index

            width: resultsList.width
            height: 50
            radius: 8
            color: index === content.controller.activeIndex ? "#26ffffff"
                : rowMouse.containsMouse ? "#12ffffff" : "transparent"
            border.color: index === content.controller.activeIndex ? "#40ffffff" : "transparent"
            border.width: 1

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 8
                anchors.rightMargin: 12
                spacing: 12

                Item {
                    Layout.preferredWidth: 34
                    Layout.preferredHeight: 34
                    RoundedArtwork {
                        anchors.fill: parent
                        radius: rowItem.modelData.category === "artists" ? 17 : 5
                        source: rowItem.modelData.artwork || ""
                        visible: Boolean(rowItem.modelData.artwork)
                    }
                    Rectangle {
                        anchors.fill: parent
                        radius: 6
                        color: "#1a221d"
                        visible: !rowItem.modelData.artwork
                        LucideIcon {
                            anchors.centerIn: parent
                            width: 17
                            height: 17
                            name: rowItem.modelData.icon || "search"
                            color: rowItem.index === content.controller.activeIndex ? "#ffffff" : "#d0d7d2"
                        }
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 2
                    Text {
                        Layout.fillWidth: true
                        text: rowItem.modelData.title || ""
                        color: "#ffffff"
                        font.family: "Inter"
                        font.pixelSize: 13
                        font.weight: Font.DemiBold
                        elide: Text.ElideRight
                    }
                    Text {
                        Layout.fillWidth: true
                        text: rowItem.modelData.subtitle || ""
                        color: rowItem.index === content.controller.activeIndex ? "#edf1ee" : "#d0d7d2"
                        font.family: "Inter"
                        font.pixelSize: 11
                        elide: Text.ElideRight
                        visible: text.length > 0
                    }
                }

                Rectangle {
                    Layout.preferredHeight: 20
                    Layout.preferredWidth: typeLabel.implicitWidth + 12
                    visible: Boolean(rowItem.modelData.type)
                    radius: 4
                    color: rowItem.index === content.controller.activeIndex ? "#32ffffff" : "#20ffffff"
                    Text {
                        id: typeLabel
                        anchors.centerIn: parent
                        text: rowItem.modelData.type || ""
                        color: rowItem.index === content.controller.activeIndex ? "#ffffff" : "#e0e5e1"
                        font.family: "Inter"
                        font.pixelSize: 10
                        font.weight: Font.Medium
                    }
                }
            }

            MouseArea {
                id: rowMouse
                anchors.fill: parent
                hoverEnabled: true
                cursorShape: Qt.PointingHandCursor
                onEntered: content.controller.activeIndex = rowItem.index
                onClicked: content.controller.activateRow(rowItem.modelData)
            }
        }

        FlickableWheel { view: resultsList }
    }

    Item {
        Layout.fillWidth: true
        Layout.fillHeight: true
        visible: content.controller.spotlightRows.length === 0
        ColumnLayout {
            anchors.centerIn: parent
            spacing: 10
            BusyIndicator {
                Layout.alignment: Qt.AlignHCenter
                running: NumaSearch.loading
                visible: running
            }
            LucideIcon {
                Layout.alignment: Qt.AlignHCenter
                Layout.preferredWidth: 32
                Layout.preferredHeight: 32
                name: "search"
                color: "#c1cac3"
                visible: !NumaSearch.loading
            }
            Text {
                Layout.alignment: Qt.AlignHCenter
                text: NumaSearch.loading ? qsTr("Searching…") : qsTr("No matches found")
                color: "#d5dbd7"
                font.family: "Inter"
                font.pixelSize: 13
            }
        }
    }

    Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 1; color: "#20ffffff" }

    RowLayout {
        Layout.fillWidth: true
        Layout.preferredHeight: 28
        Layout.leftMargin: 14
        Layout.rightMargin: 14
        Text {
            text: qsTr("↑↓ Navigate   ↵ Select   Esc Close")
            color: "#d0d7d2"
            font.family: "Inter"
            font.pixelSize: 10
        }
        Item { Layout.fillWidth: true }
        Text {
            text: qsTr("Spotlight")
            color: "#eef1ef"
            font.family: "Inter"
            font.pixelSize: 10
            font.weight: Font.DemiBold
        }
    }
}
