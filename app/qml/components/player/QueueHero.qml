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
import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import QtQuick.Layouts

// Now playing card: cover, title and progress on a flat surface.
Rectangle {
    id: hero
    required property var panel

    function timeLabel(seconds) {
        if (!Number.isFinite(seconds) || seconds < 0)
            return "0:00";
        const total = Math.floor(seconds);
        return Math.floor(total / 60) + ":" + String(total % 60).padStart(2, "0");
    }

    Layout.fillWidth: true
    Layout.preferredHeight: 146
    radius: 16
    color: panel.tint(panel.accentColor, 0.08)
    antialiasing: true

    RoundedSurfaceFrame {
        anchors.fill: parent
        radius: hero.radius
        softGlow: true
        showBorder: true
        fillColor: "transparent"
        border.color: panel.tint(panel.accentColor, 0.32)
        glowColor: panel.accentColor
    }

    RowLayout {
        anchors.fill: parent
        anchors.margins: 14
        anchors.bottomMargin: 18
        spacing: 14

        TransitionArtwork {
            id: heroArt
            Layout.preferredWidth: 96
            Layout.preferredHeight: 96
            radius: 14
            source: NumaPlayback.track.thumbnail || ""
            incomingSource: NumaPlayback.transitionTrack.thumbnail || ""
            transitioning: NumaPlayback.crossfadeActive
            progress: NumaPlayback.crossfadeProgress
            dissolve: true

            MouseArea {
                anchors.fill: parent
                z: 2
                enabled: panel.compactMode && Boolean(NumaPlayback.track.id)
                hoverEnabled: true
                cursorShape: enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
                onClicked: panel.showCompactArtwork()
            }

            // Motion cover plays over the still one and bows out early in a crossfade.
            Loader {
                anchors.fill: parent
                active: NumaAppearance.animatedArtworkEnabled
                        && Boolean(NumaPlayback.animatedArtworkUrl)
                        && panel.visible && panel.motionArtwork
                opacity: NumaPlayback.crossfadeActive ? 1 - Math.min(1, NumaPlayback.crossfadeProgress / 0.15) : 1

                sourceComponent: Component {
                    AnimatedArtwork {
                        radius: heroArt.radius
                        source: NumaPlayback.animatedArtworkUrl
                        playing: NumaPlayback.playing
                    }
                }
            }
        }

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 3

            RowLayout {
                spacing: 6

                // Tiny equalizer. It dances whether or not the song deserves it.
                Item {
                    Layout.preferredWidth: 12
                    Layout.preferredHeight: 10
                    Repeater {
                        model: 3
                        Rectangle {
                            id: bar
                            required property int index
                            x: index * 4.5
                            y: 10 - height
                            width: 2.5
                            radius: 1
                            color: panel.accentColor
                            height: 3
                            SequentialAnimation on height {
                                running: NumaPlayback.playing && panel.visible
                                loops: Animation.Infinite
                                onRunningChanged: if (!running) bar.height = 3
                                NumberAnimation { to: 10; duration: 260 + bar.index * 90; easing.type: Easing.InOutSine }
                                NumberAnimation { to: 3 + bar.index; duration: 220 + bar.index * 70; easing.type: Easing.InOutSine }
                            }
                        }
                    }
                }

                Text {
                    visible: !NumaPlayback.crossfadeActive
                    text: NumaPlayback.playing ? qsTr("NOW PLAYING") : qsTr("PAUSED")
                    color: panel.accentColor
                    font.family: "Inter"
                    font.pixelSize: 10
                    font.bold: true
                    font.letterSpacing: 0.9
                }

                Row {
                    id: heroMixLabel
                    property real sweep: 0
                    visible: NumaPlayback.crossfadeActive

                    NumberAnimation on sweep {
                        running: heroMixLabel.visible && panel.visible
                        from: -2
                        to: 9
                        duration: 1800
                        loops: Animation.Infinite
                    }

                    Repeater {
                        model: qsTr("Mixing").split("")

                        Text {
                            required property string modelData
                            required property int index
                            text: modelData.toUpperCase()
                            color: panel.mixAccentColor
                            opacity: 0.45 + 0.55 * Math.max(0, 1 - Math.abs(index - heroMixLabel.sweep) / 2)
                            font.family: "Inter"
                            font.pixelSize: 10
                            font.bold: true
                            font.letterSpacing: 0.9
                        }
                    }
                }

                Text {
                    visible: NumaPlayback.crossfadeActive && NumaPlayback.mixDuration > 0
                    text: "(" + hero.timeLabel(NumaPlayback.mixDuration) + ")"
                    color: panel.mixAccentColor
                    font.family: "Inter"
                    font.pixelSize: 10
                    font.bold: true
                    font.letterSpacing: 0.5
                }
            }

            Text {
                objectName: "queueCurrentTitle"
                Layout.fillWidth: true
                text: NumaPlayback.track.title || qsTr("Nothing playing")
                color: panel.primaryText
                font.family: "Inter"
                font.pixelSize: 15
                font.bold: true
                font.letterSpacing: -0.2
                elide: Text.ElideRight
            }

            Text {
                Layout.fillWidth: true
                text: (NumaPlayback.track.artists || []).join(", ") || NumaPlayback.track.artist || ""
                visible: text.length > 0
                color: panel.secondaryText
                font.family: "Inter"
                font.pixelSize: 12
                elide: Text.ElideRight
            }

            Text {
                Layout.fillWidth: true
                objectName: "queueTransitionLabel"
                readonly property string mixStyle: NumaPlayback.adaptiveMix.mixStyle
                readonly property int mixBpm: Math.round(NumaPlayback.adaptiveMix.mixBpm)
                readonly property string incomingTitle: NumaPlayback.transitionTrack.title || ""
                visible: NumaPlayback.crossfadeActive && incomingTitle.length > 0
                // Name the move while it happens. Credit where credit is due, DJ.
                text: mixStyle.length === 0 ? qsTr("into %1").arg(incomingTitle)
                    : mixBpm > 0 ? qsTr("%1 at %2 BPM into %3").arg(mixStyle).arg(mixBpm).arg(incomingTitle)
                    : qsTr("%1 into %2").arg(mixStyle).arg(incomingTitle)
                color: panel.mutedText
                font.family: "Inter"
                font.pixelSize: 11
                font.italic: true
                elide: Text.ElideRight
            }

            RowLayout {
                spacing: 8
                Layout.topMargin: 2

                Rectangle {
                    width: 26
                    height: 26
                    radius: 13
                    color: prevMouse.pressed ? "#26ffffff" : prevMouse.containsMouse ? "#1affffff" : "transparent"
                    opacity: NumaPlayback.canGoPrevious ? 1 : 0.4
                    LucideIcon {
                        anchors.centerIn: parent
                        width: 15
                        height: 15
                        name: "skip-back"
                        color: panel.primaryText
                    }
                    MouseArea {
                        id: prevMouse
                        anchors.fill: parent
                        hoverEnabled: true
                        cursorShape: NumaPlayback.canGoPrevious ? Qt.PointingHandCursor : Qt.ArrowCursor
                        onClicked: NumaPlayback.previous()
                    }
                }

                Rectangle {
                    width: 30
                    height: 30
                    radius: 15
                    color: playMouse.pressed ? Qt.darker(panel.accentColor, 1.15) : playMouse.containsMouse ? Qt.lighter(panel.accentColor, 1.08) : panel.accentColor
                    LucideIcon {
                        anchors.centerIn: parent
                        width: 16
                        height: 16
                        name: NumaPlayback.playing ? "pause" : "play"
                        color: panel.inkColor
                    }
                    MouseArea {
                        id: playMouse
                        anchors.fill: parent
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: NumaPlayback.toggle()
                    }
                }

                Rectangle {
                    width: 26
                    height: 26
                    radius: 13
                    color: nextMouse.pressed ? "#26ffffff" : nextMouse.containsMouse ? "#1affffff" : "transparent"
                    opacity: NumaPlayback.canGoNext ? 1 : 0.4
                    LucideIcon {
                        anchors.centerIn: parent
                        width: 15
                        height: 15
                        name: "skip-forward"
                        color: panel.primaryText
                    }
                    MouseArea {
                        id: nextMouse
                        anchors.fill: parent
                        hoverEnabled: true
                        cursorShape: NumaPlayback.canGoNext ? Qt.PointingHandCursor : Qt.ArrowCursor
                        onClicked: NumaPlayback.next()
                    }
                }

                Rectangle {
                    width: 26
                    height: 26
                    radius: 13
                    color: shufMouse.pressed ? "#26ffffff" : shufMouse.containsMouse ? "#1affffff" : "transparent"
                    LucideIcon {
                        anchors.centerIn: parent
                        width: 14
                        height: 14
                        name: "shuffle"
                        color: NumaPlayback.shuffleEnabled ? panel.accentColor : panel.secondaryText
                    }
                    MouseArea {
                        id: shufMouse
                        anchors.fill: parent
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: NumaPlayback.shuffleEnabled = !NumaPlayback.shuffleEnabled
                    }
                }

                Rectangle {
                    width: 26
                    height: 26
                    radius: 13
                    color: repMouse.pressed ? "#26ffffff" : repMouse.containsMouse ? "#1affffff" : "transparent"
                    LucideIcon {
                        anchors.centerIn: parent
                        width: 14
                        height: 14
                        name: NumaPlayback.repeatMode === "one" ? "repeat-1" : "repeat"
                        color: NumaPlayback.repeatMode !== "off" ? panel.accentColor : panel.secondaryText
                    }
                    MouseArea {
                        id: repMouse
                        anchors.fill: parent
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: NumaPlayback.cycleRepeatMode()
                    }
                }
            }
        }
    }

    // Progress rail inset along the bottom of the card.
    Rectangle {
        id: heroRail
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.leftMargin: 14
        anchors.rightMargin: 14
        anchors.bottomMargin: 10
        height: mixAmount > 0.01 ? 6 : 4
        radius: height / 2
        color: "#1fffffff"
        antialiasing: true
        Behavior on height { NumberAnimation { duration: 220; easing.type: Easing.OutBack; easing.overshoot: 2 } }

        property real mixAmount: NumaPlayback.crossfadeActive ? 1 : 0
        Behavior on mixAmount { NumberAnimation { duration: 600; easing.type: Easing.OutCubic } }

        MixDotFill {
            anchors.fill: parent
            fraction: NumaPlayback.displayDuration > 0
                   ? Math.max(0, Math.min(1, NumaPlayback.displayPosition / NumaPlayback.displayDuration))
                   : 0
            baseColor: panel.accentColor
            dotColor: panel.mixAccentColor
            amount: heroRail.mixAmount
            maxBand: 180
            rows: 2
            layer.enabled: heroRail.mixAmount > 0.01
            layer.effect: MultiEffect {
                autoPaddingEnabled: true
                shadowEnabled: true
                shadowColor: panel.mixAccentColor
                shadowBlur: 0.6
                shadowOpacity: 0.6 * heroRail.mixAmount
                shadowHorizontalOffset: 0
                shadowVerticalOffset: 0
            }
        }

        MouseArea {
            anchors.fill: parent
            anchors.margins: -4
            cursorShape: Qt.PointingHandCursor
            onClicked: function(mouse) {
                if (NumaPlayback.duration > 0) {
                    const fraction = Math.max(0, Math.min(1, mouse.x / width));
                    NumaPlayback.seek(fraction * NumaPlayback.duration);
                }
            }
        }
    }
}
