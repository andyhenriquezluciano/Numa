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
import ".."
import "../home"

// Frosted "Up next" rail laid over the right edge of the picture.
Rectangle {
    id: root

    signal closeRequested()
    property Item videoBackdrop: null

    function durationLabel(track) {
        if (track.duration)
            return String(track.duration);
        const seconds = Math.max(0, Math.round(Number(track.durationSeconds || 0)));
        return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
    }

    color: "transparent"
    radius: 20

    PopupBloomSurface {
        anchors.fill: parent
        radius: 20
        backdrop: root.videoBackdrop
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        anchors.topMargin: 20
        spacing: 8

        RowLayout {
            Layout.fillWidth: true
            Layout.leftMargin: 8

            Label {
                Layout.fillWidth: true
                text: qsTr("Queue") + " · " + list.count
                color: "#f7f5f0"
                font.pixelSize: 19
                font.weight: Font.DemiBold
            }

            TheaterButton {
                glyph: "x"
                label: qsTr("Close up next")
                size: 36
                iconSize: 18
                onClicked: root.closeRequested()
            }
        }

        Label {
            text: qsTr("NOW PLAYING")
            color: "#a6aaa3"
            font.pixelSize: 10
            font.letterSpacing: 1.4
            Layout.topMargin: 8
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 58
            radius: 12
            color: "#20ffffff"
            border.color: "#30ffffff"
            RowLayout {
                anchors.fill: parent
                anchors.margins: 9
                spacing: 10
                RoundedArtwork {
                    Layout.preferredWidth: 40
                    Layout.preferredHeight: 40
                    source: NumaPlayback.track.thumbnail || ""
                    radius: 6
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 3
                    Label {
                        Layout.fillWidth: true
                        text: NumaPlayback.track.title || ""
                        color: "#f7f5f0"
                        font.pixelSize: 12
                        font.weight: Font.DemiBold
                        elide: Text.ElideRight
                    }
                    Label {
                        Layout.fillWidth: true
                        text: NumaPlayback.track.artist || (NumaPlayback.track.artists || []).join(", ")
                        color: "#a6aaa3"
                        font.pixelSize: 11
                        elide: Text.ElideRight
                    }
                }
                Label {
                    text: root.durationLabel(NumaPlayback.track)
                    color: "#a6aaa3"
                    font.pixelSize: 11
                }
            }
        }

        Label {
            text: qsTr("UP NEXT")
            color: "#a6aaa3"
            font.pixelSize: 10
            font.letterSpacing: 1.4
            Layout.topMargin: 8
        }

        Label {
            visible: list.count === 0
            Layout.leftMargin: 8
            text: qsTr("Nothing queued.")
            color: "#8d928a"
            font.pixelSize: 14
        }

        ListView {
            id: list

            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            spacing: 2
            model: NumaPlayback.queue || []
            boundsBehavior: Flickable.StopAtBounds

            delegate: ItemDelegate {
                id: row

                required property var modelData
                required property int index

                width: ListView.view.width
                height: 58
                hoverEnabled: true
                enabled: !NumaPlayback.loading
                Accessible.name: qsTr("Play %1").arg(modelData.title || "")
                onClicked: NumaPlayback.playQueueIndex(index)

                background: Rectangle {
                    radius: 10
                    color: row.hovered ? "#20ffffff" : row.index === 0 ? "#12ffffff" : "transparent"
                    border.color: row.activeFocus || row.index === 0 ? "#30ffffff" : "transparent"
                }

                contentItem: RowLayout {
                    spacing: 10

                    Rectangle {
                        Layout.preferredWidth: 40
                        Layout.preferredHeight: 40
                        radius: 6
                        color: "#1b1d20"
                        clip: true

                        RoundedArtwork {
                            anchors.fill: parent
                            source: row.modelData.thumbnail || ""
                            radius: 6
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 4

                        Label {
                            Layout.fillWidth: true
                            text: row.modelData.title || ""
                            color: "#f7f5f0"
                            font.pixelSize: 12
                            font.weight: Font.DemiBold
                            elide: Text.ElideRight
                        }

                        Label {
                            Layout.fillWidth: true
                            text: (row.modelData.artists || []).join(", ") || row.modelData.artist || ""
                            color: "#a6aaa3"
                            font.pixelSize: 11
                            elide: Text.ElideRight
                        }
                    }
                    Label {
                        text: root.durationLabel(row.modelData)
                        color: "#a6aaa3"
                        font.pixelSize: 11
                    }
                }
            }

            FlickableWheel {
                view: list
            }
            ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 54
            radius: 14
            color: "#10ffffff"
            border.color: "#1affffff"
            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 12
                anchors.rightMargin: 8
                Label {
                    Layout.fillWidth: true
                    text: qsTr("Autoplay")
                    color: "#f7f5f0"
                    font.pixelSize: 12
                }
                Switch {
                    checked: NumaPlayback.autoplayEnabled
                    onToggled: NumaPlayback.autoplayEnabled = checked
                    Accessible.name: qsTr("Autoplay")
                }
            }
        }
    }
}
