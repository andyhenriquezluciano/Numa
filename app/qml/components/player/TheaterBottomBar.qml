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
import QtQuick.Layouts
import QtQuick.Window

// Lower letterbox bar: scrubber on the picture edge, then title, transport and utilities.
Item {
    id: root

    property color accentColor: NumaAppearance.textColor
    property Item videoBackdrop: null
    signal fullscreenRequested()
    readonly property bool menuOpen: qualityMenu.visible

    readonly property var track: NumaPlayback.track || ({})
    readonly property bool windowFullScreen: NumaWindowState.videoFullScreen

    readonly property var heightOptions: NumaMusicVideo.heights || []

    component QualityItem: MenuItem {
        id: item

        implicitWidth: 204
        implicitHeight: 36
        checkable: true
        indicator: null

        contentItem: Row {
            spacing: 10
            leftPadding: 6

            LucideIcon {
                anchors.verticalCenter: parent.verticalCenter
                name: "check"
                color: root.accentColor
                width: 14
                height: 14
                opacity: item.checked ? 1 : 0
            }

            Label {
                anchors.verticalCenter: parent.verticalCenter
                text: item.text
                color: item.checked ? "#ffffff" : "#dedbd4"
                font.pixelSize: 13
                font.weight: item.checked ? Font.DemiBold : Font.Normal
            }
        }

        background: Rectangle {
            radius: 8
            color: item.highlighted ? "#24ffffff" : "transparent"
            Behavior on color { ColorAnimation { duration: 90 } }
        }
    }

    function heightLabel(height) {
        return height >= 2160 ? qsTr("4K") : height + "p";
    }

    function timeLabel(seconds) {
        if (!Number.isFinite(seconds) || seconds < 0)
            return "0:00";
        const total = Math.floor(seconds);
        return Math.floor(total / 60) + ":" + String(total % 60).padStart(2, "0");
    }

    Slider {
        id: scrubber

        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: -9
        height: 18
        from: 0
        to: Math.max(1, NumaPlayback.duration)
        enabled: NumaPlayback.duration > 0
        hoverEnabled: true
        Accessible.name: qsTr("Seek")
        // Holds the drag position until release; seeking per pixel would thrash the stream.
        onPressedChanged: if (!pressed) NumaPlayback.seek(value)

        Binding on value {
            value: NumaPlayback.position
            when: !scrubber.pressed
        }

        background: Rectangle {
            x: scrubber.leftPadding
            y: scrubber.topPadding + scrubber.availableHeight / 2 - height / 2
            width: scrubber.availableWidth
            height: scrubber.hovered || scrubber.pressed ? 5 : 3
            radius: height / 2
            color: "#2effffff"

            Rectangle {
                width: scrubber.visualPosition * parent.width
                height: parent.height
                radius: parent.radius
                color: root.accentColor
            }

            // SponsorBlock spans, drawn over the progress so they read in both halves.
            Repeater {
                model: NumaPlayback.skipSegments || []

                Rectangle {
                    required property var modelData

                    x: parent.width * modelData.startTime / scrubber.to
                    width: Math.max(2, parent.width * (modelData.endTime - modelData.startTime) / scrubber.to)
                    height: parent.height
                    color: modelData.category === "music_offtopic" ? "#ff9f43" : "#c4a7e7"
                    opacity: 0.85
                }
            }

            Behavior on height { NumberAnimation { duration: 90 } }
        }

        handle: Rectangle {
            x: scrubber.leftPadding + scrubber.visualPosition * (scrubber.availableWidth - width)
            y: scrubber.topPadding + scrubber.availableHeight / 2 - height / 2
            width: 12
            height: 12
            radius: 6
            color: root.accentColor
            scale: scrubber.pressed ? 1.2 : scrubber.hovered ? 1 : 0.85

            Behavior on scale { NumberAnimation { duration: 90 } }
        }
    }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 40
        anchors.rightMargin: 40
        anchors.topMargin: 14
        spacing: 24

        ColumnLayout {
            Layout.fillWidth: true
            Layout.preferredWidth: 1
            Layout.alignment: Qt.AlignVCenter
            spacing: 6

            Label {
                Layout.fillWidth: true
                text: root.track.title || ""
                color: "#f7f5f0"
                font.pixelSize: 32
                font.weight: Font.Bold
                font.letterSpacing: -0.6
                elide: Text.ElideRight
            }

            Label {
                Layout.fillWidth: true
                text: (root.track.artists || []).join(", ") || root.track.artist || ""
                color: "#c3c6bf"
                font.pixelSize: 15
                elide: Text.ElideRight
            }
        }

        RowLayout {
            Layout.alignment: Qt.AlignVCenter
            spacing: 20

            TheaterButton {
                glyph: "skip-back"
                label: qsTr("Previous")
                size: 48
                iconSize: 22
                enabled: NumaPlayback.canGoPrevious
                onClicked: NumaPlayback.previous()
            }

            TheaterButton {
                glyph: NumaPlayback.playing ? "pause" : "play"
                label: NumaPlayback.playing ? qsTr("Pause") : qsTr("Play")
                size: 64
                iconSize: 24
                fill: root.accentColor
                iconColor: "#0b0d0a"
                onClicked: NumaPlayback.toggle()
            }

            TheaterButton {
                glyph: "skip-forward"
                label: qsTr("Next")
                size: 48
                iconSize: 22
                enabled: NumaPlayback.canGoNext
                onClicked: NumaPlayback.next()
            }
        }

        RowLayout {
            Layout.fillWidth: true
            Layout.preferredWidth: 1
            Layout.alignment: Qt.AlignVCenter
            spacing: 6

            Item { Layout.fillWidth: true }

            Label {
                Layout.rightMargin: 10
                text: root.timeLabel(scrubber.pressed ? scrubber.value : NumaPlayback.position)
                      + "  /  " + root.timeLabel(NumaPlayback.duration)
                color: "#c3c6bf"
                font.pixelSize: 13
                font.features: { "tnum": 1 }
            }

            LikeButton {
                implicitWidth: 44
                implicitHeight: 44
                iconSize: 20
                iconColor: "#f7f5f0"
            }

            Button {
                id: qualityButton

                implicitHeight: 36
                hoverEnabled: true
                focusPolicy: Qt.TabFocus
                Accessible.name: qsTr("Video quality")
                ToolTip.visible: hovered && !qualityMenu.visible
                ToolTip.delay: 500
                ToolTip.text: qsTr("Video quality")
                onClicked: qualityMenu.visible ? qualityMenu.close() : qualityMenu.open()

                background: Rectangle {
                    radius: 18
                    color: qualityButton.hovered || qualityMenu.visible ? "#1fffffff" : "#0fffffff"
                    border.color: qualityButton.activeFocus ? root.accentColor : "#14ffffff"
                }

                contentItem: Label {
                    text: NumaMusicVideo.height > 0 ? root.heightLabel(NumaMusicVideo.height) : qsTr("Quality")
                    color: "#f7f5f0"
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                    font.features: { "tnum": 1 }
                    leftPadding: 12
                    rightPadding: 12
                    verticalAlignment: Text.AlignVCenter
                }

                Menu {
                    id: qualityMenu

                    Component.onCompleted: {
                        if ("popupType" in qualityMenu)
                            qualityMenu["popupType"] = 0;
                    }

                    y: -implicitHeight - 10
                    x: qualityButton.width - width

                    padding: 6

                    enter: Transition {
                        NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 110; easing.type: Easing.OutQuad }
                        NumberAnimation { property: "scale"; from: 0.96; to: 1; duration: 110; easing.type: Easing.OutQuad }
                    }
                    exit: Transition {
                        NumberAnimation { property: "opacity"; to: 0; duration: 80; easing.type: Easing.InQuad }
                    }

                    background: PopupBloomSurface {
                        implicitWidth: 216
                        radius: 14
                        backdrop: root.videoBackdrop

                        Rectangle {
                            anchors.fill: parent
                            anchors.margins: 1
                            radius: 13
                            color: "transparent"
                            border.color: "#12ffffff"
                            border.width: 1
                            antialiasing: true
                        }

                        Rectangle {
                            anchors.top: parent.top
                            anchors.left: parent.left
                            anchors.right: parent.right
                            height: 36
                            radius: 14
                            color: "transparent"
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: "#16ffffff" }
                                GradientStop { position: 1.0; color: "transparent" }
                            }
                        }
                    }

                    QualityItem {
                        text: qsTr("Best available")
                        checked: NumaMusicVideo.maxHeight === 0
                        onTriggered: NumaMusicVideo.maxHeight = 0
                    }

                    Repeater {
                        model: root.heightOptions

                        QualityItem {
                            required property int modelData

                            text: root.heightLabel(modelData)
                            checked: NumaMusicVideo.maxHeight === modelData
                            onTriggered: NumaMusicVideo.maxHeight = modelData
                        }
                    }
                }
            }

            TheaterButton {
                glyph: root.windowFullScreen ? "minimize" : "maximize"
                label: root.windowFullScreen ? qsTr("Exit full screen") : qsTr("Full screen")
                onClicked: root.fullscreenRequested()
            }
        }
    }
}
