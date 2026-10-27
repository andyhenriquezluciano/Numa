/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Layouts
import "."
import "home"

Rectangle {
    id: row

    required property var controller
    required property var modelData
    required property int index

    width: ListView.view ? ListView.view.width : 0
    height: 48
    radius: 7
    color: index === controller.activeIndex ? "#1effffff"
        : rowMouse.containsMouse ? "#0dffffff" : "transparent"
    border.color: index === controller.activeIndex ? "#30ffffff" : "transparent"
    border.width: 1

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 8
        anchors.rightMargin: 10
        spacing: 10

        Item {
            Layout.preferredWidth: 32
            Layout.preferredHeight: 32
            RoundedArtwork {
                anchors.fill: parent
                radius: 5
                source: row.modelData.artwork || ""
                visible: Boolean(row.modelData.artwork)
            }
            Rectangle {
                anchors.fill: parent
                radius: 5
                color: "#1a221d"
                visible: !row.modelData.artwork
                LucideIcon {
                    anchors.centerIn: parent
                    width: 16
                    height: 16
                    name: "music-2"
                    color: row.index === row.controller.activeIndex ? "#e3e9e4" : "#9ca7a0"
                }
            }
        }

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2
            Text {
                Layout.fillWidth: true
                text: row.modelData.title || ""
                color: row.index === row.controller.activeIndex ? "#ffffff" : "#f0f3f0"
                font.family: "Inter"
                font.pixelSize: 13
                font.weight: Font.DemiBold
                elide: Text.ElideRight
            }
            Text {
                Layout.fillWidth: true
                text: row.modelData.subtitle || ""
                color: row.index === row.controller.activeIndex ? "#b0bcb2" : "#838f85"
                font.family: "Inter"
                font.pixelSize: 11
                elide: Text.ElideRight
                visible: text.length > 0
            }
        }

        Text {
            text: String(row.modelData.index + 1)
            color: row.index === row.controller.activeIndex ? "#adb8af" : "#69746c"
            font.family: "Inter"
            font.pixelSize: 11
            font.weight: Font.Medium
        }
    }

    MouseArea {
        id: rowMouse
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onEntered: {
            row.controller.activeIndex = row.index;
            row.controller.notifyMatchFocus();
        }
        onClicked: {
            row.controller.close();
            row.controller.trackSelected(row.modelData.track, row.modelData.index);
        }
    }
}
