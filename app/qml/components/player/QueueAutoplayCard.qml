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
import "../home"
import "../settings"
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// Autoplay toggle with load and error state.
Rectangle {
    id: autoplayCard
    required property var panel

    Layout.fillWidth: true
    Layout.preferredHeight: implicitHeight * (1 - panel.lyricsReveal)
    Layout.topMargin: -12 * panel.lyricsReveal
    visible: panel.lyricsReveal < 0.999
    opacity: 1 - panel.lyricsReveal
    clip: true
    implicitHeight: autoplayLayout.implicitHeight + 20
    radius: 14
    color: NumaAppearance.rosePine ? "transparent" : "#10ffffff"
    border.color: NumaAppearance.rosePine ? "transparent" : "#1affffff"

    RowLayout {
        id: autoplayLayout
        anchors.fill: parent
        anchors.margins: 10
        spacing: 10

        Rectangle {
            Layout.preferredWidth: 34
            Layout.preferredHeight: 34
            radius: 10
            color: NumaPlayback.autoplayEnabled ? panel.tint(panel.accentColor, 0.18) : "#14ffffff"
            Behavior on color { ColorAnimation { duration: 200 } }

            LucideIcon {
                anchors.centerIn: parent
                name: "infinity"
                width: 18
                height: 18
                color: NumaPlayback.autoplayEnabled ? panel.accentColor : panel.mutedText
            }
        }

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 2

            Text {
                text: qsTr("Autoplay")
                color: panel.primaryText
                font.family: "Inter"
                font.pixelSize: 12
                font.bold: true
            }

            Text {
                Layout.fillWidth: true
                text: NumaPlayback.autoplayLoading ? qsTr("Finding more music…")
                      : NumaPlayback.autoplayError || qsTr("Keep the music going")
                color: NumaPlayback.autoplayError ? "#e6a197" : panel.mutedText
                wrapMode: Text.Wrap
                font.family: "Inter"
                font.pixelSize: 10
            }

            Button {
                id: retryAutoplay
                visible: Boolean(NumaPlayback.autoplayError)
                text: qsTr("Retry")
                implicitHeight: 22
                leftPadding: 8
                rightPadding: 8
                onClicked: NumaPlayback.retryAutoplay()
                background: Rectangle {
                    radius: 6
                    color: retryAutoplay.hovered ? "#26ffffff" : "#14ffffff"
                }
                contentItem: Text {
                    text: retryAutoplay.text
                    color: panel.primaryText
                    font.family: "Inter"
                    font.pixelSize: 11
                    font.bold: true
                    verticalAlignment: Text.AlignVCenter
                }
            }
        }

        SettingsSwitch {
            objectName: "queueAutoplay"
            checked: NumaPlayback.autoplayEnabled
            Accessible.name: qsTr("Autoplay")
            onToggled: NumaPlayback.autoplayEnabled = checked
        }
    }
}
