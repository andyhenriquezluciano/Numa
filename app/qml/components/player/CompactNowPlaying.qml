/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import ".."
import "../home"
import Numa
import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import QtQuick.Layouts
import QtQuick.Window

Item {
    id: popup

    required property var panel
    property bool open: false
    property real rememberedVolume: 0.7
    readonly property bool hasTrack: Boolean(NumaPlayback.track.id)
    readonly property real volumeLevel: NumaHome.volume
    property real artworkPulse: bassPulse.level
    Behavior on artworkPulse { NumberAnimation { duration: 60 } }
    readonly property bool artworkReactive: bassPulse.live
    property real mixAmount: NumaPlayback.crossfadeActive ? 1 : 0
    Behavior on mixAmount { NumberAnimation { duration: 700; easing.type: Easing.OutCubic } }
    readonly property color mixAccentColor: panel && panel.mixAccentColor ? panel.mixAccentColor : (panel ? panel.accentColor : NumaAppearance.textColor)

    function setVolume(value) {
        const level = Math.max(0, Math.min(1, value));
        if (level > 0.001) rememberedVolume = level;
        NumaHome.volume = level;
    }

    function timeLabel(seconds) {
        if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
        const total = Math.floor(seconds);
        return Math.floor(total / 60) + ":" + String(total % 60).padStart(2, "0");
    }

    visible: opacity > 0.001
    enabled: open
    opacity: open ? 1 : 0
    z: 50
    focus: open
    Keys.onEscapePressed: open = false
    onOpenChanged: {
        if (open)
            forceActiveFocus();
        else
            panel.lyricsMode = false;
    }
    Behavior on opacity { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }

    BassPulse {
        id: bassPulse
        engine: NumaPlayback.audioEngine
        running: popup.open && !panel.lyricsMode && NumaPlayback.playing
                 && !NumaAppearance.performanceMode
    }

    Rectangle {
        id: card
        anchors.fill: parent
        radius: panel.radius
        color: "transparent"
        antialiasing: true

        Glass {
            anchors.fill: parent; backdrop: panel.backdrop; radius: panel.radius
            live: panel.backdropLive && popup.visible
            shadowOpacity: 0; shadowSpread: 0; shadowOffsetY: 0
        }

        Rectangle {
            anchors.fill: parent; radius: panel.radius; antialiasing: true
            color: NumaAppearance.rosePine ? NumaAppearance.surfaceColor : panel.tint(panel.inkColor, panel.compactMode ? 0.34 : 0.6)
            border.color: panel.compactMode ? "transparent" : "#26ffffff"
            border.width: panel.compactMode ? 0 : 1
        }

        Rectangle {
            anchors.fill: parent; radius: panel.radius; antialiasing: true
            visible: !NumaAppearance.performanceMode
            gradient: Gradient {
                GradientStop { position: 0; color: panel.tint(panel.accentColor, panel.compactMode ? 0.08 : 0.16) }
                GradientStop { position: 0.4; color: "transparent" }
            }
        }

        MouseArea {
            anchors.fill: parent
            acceptedButtons: Qt.AllButtons
            onWheel: function(wheel) { wheel.accepted = true; }
        }

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 16
            spacing: 10

            RowLayout {
                Layout.fillWidth: true
                spacing: 8

                DragHandler {
                    target: null
                    grabPermissions: PointerHandler.ApprovesTakeOverByAnything
                    onActiveChanged: {
                        if (active && popup.Window.window)
                            popup.Window.window.startSystemMove();
                    }
                }

                CompactWindowButton {
                    objectName: "compactBackToQueue"
                    colors: panel
                    glyph: "chevron-left"
                    label: qsTr("Show queue")
                    onClicked: popup.open = false
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 1

                    Text {
                        Layout.fillWidth: true
                        text: panel.lyricsMode ? qsTr("Lyrics") : NumaPlayback.track.title || qsTr("Nothing playing")
                        color: panel.primaryText
                        font.family: "Inter"
                        font.pixelSize: 15
                        font.bold: true
                        elide: Text.ElideRight
                    }

                    Text {
                        Layout.fillWidth: true
                        text: panel.lyricsMode
                              ? (NumaLyrics.status === "ready" ? (NumaLyrics.mode === "synced" ? qsTr("Synced to the music") : qsTr("Not time-synced"))
                                 : NumaLyrics.status === "loading" ? qsTr("Looking around…") : qsTr("Nothing to sing along to"))
                              : (NumaPlayback.track.artists || []).join(", ") || NumaPlayback.track.artist || ""
                        color: panel.secondaryText
                        font.family: "Inter"
                        font.pixelSize: 11
                        elide: Text.ElideRight
                    }
                }

                CompactWindowButton {
                    colors: panel; glyph: panel.lyricsMode ? "image" : "mic-vocal"
                    label: panel.lyricsMode ? qsTr("Show artwork") : qsTr("Show lyrics")
                    active: panel.lyricsMode; onClicked: panel.lyricsMode = !panel.lyricsMode
                }
                CompactWindowButton {
                    colors: panel; glyph: "maximize"; label: qsTr("Exit compact mode")
                    onClicked: { popup.open = false; panel.compactExitRequested(); }
                }
                CompactWindowButton {
                    colors: panel; glyph: ""; minimizeGlyph: true; label: qsTr("Minimize")
                    onClicked: if (popup.Window.window) popup.Window.window.showMinimized()
                }
                CompactWindowButton {
                    colors: panel; glyph: "x"; label: qsTr("Close")
                    onClicked: if (popup.Window.window) popup.Window.window.close()
                }
            }

            Item {
                id: artworkStage
                readonly property real side: Math.min(card.width - 32, card.height - 204)
                Layout.fillWidth: panel.lyricsMode; Layout.fillHeight: panel.lyricsMode
                Layout.preferredWidth: panel.lyricsMode ? card.width - 32 : side
                Layout.preferredHeight: panel.lyricsMode ? 0 : side
                Layout.alignment: Qt.AlignHCenter

                Item {
                    id: glowShape
                    anchors.fill: parent; visible: false

                    Item {
                        anchors.centerIn: parent
                        width: parent.width * 0.78; height: parent.height * 0.78

                        Rectangle {
                            anchors.fill: parent; radius: Math.min(width, height) / 2
                            color: panel.accentColor; antialiasing: true
                        }

                        Image {
                            anchors.fill: parent; asynchronous: true
                            source: popup.open && !panel.lyricsMode
                                    ? (NumaPlayback.crossfadeActive && NumaPlayback.crossfadeProgress >= 0.5
                                       ? (NumaPlayback.transitionTrack.thumbnail || NumaPlayback.track.thumbnail || "")
                                       : (NumaPlayback.track.thumbnail || "")) : ""
                            sourceSize.width: 64; sourceSize.height: 64
                            fillMode: Image.PreserveAspectCrop
                            opacity: status === Image.Ready ? 1 : 0
                            Behavior on opacity { NumberAnimation { duration: 400 } }
                            layer.enabled: true
                            layer.effect: MultiEffect {
                                maskEnabled: true; maskSource: compactGlowMask
                                maskThresholdMin: 0.5; maskSpreadAtMin: 0.5
                            }
                        }

                        Rectangle {
                            id: compactGlowMask
                            anchors.fill: parent; radius: Math.min(width, height) / 2
                            antialiasing: true; visible: false
                        }
                    }
                }

                MultiEffect {
                    id: artworkGlow
                    property real breath: 0
                    anchors.fill: glowShape
                    source: glowShape
                    visible: popup.open && popup.hasTrack && !panel.lyricsMode && !NumaAppearance.performanceMode
                    autoPaddingEnabled: true
                    blurEnabled: !NumaAppearance.performanceMode
                    blur: 1
                    blurMax: 80
                    saturation: 0.35
                    scale: 1 + 0.05 * popup.artworkPulse
                    opacity: !NumaPlayback.playing ? 0.14
                             : popup.artworkReactive ? 0.3 + 0.32 * popup.artworkPulse
                                                       : 0.34 + 0.16 * breath
                    Behavior on opacity { enabled: !popup.artworkReactive; NumberAnimation { duration: 500 } }

                    SequentialAnimation on breath {
                        running: popup.open && !panel.lyricsMode && NumaPlayback.playing
                                 && !popup.artworkReactive && !NumaAppearance.performanceMode
                        loops: Animation.Infinite
                        NumberAnimation { to: 1; duration: 2600; easing.type: Easing.InOutSine }
                        NumberAnimation { to: 0; duration: 2600; easing.type: Easing.InOutSine }
                    }
                }

                Item {
                    anchors.fill: parent
                    visible: popup.open && !panel.lyricsMode
                    scale: 1 + 0.012 * popup.artworkPulse
                    transformOrigin: Item.Center

                    TransitionArtwork {
                        id: compactArt
                        anchors.fill: parent
                        radius: 24
                        source: parent.visible ? (NumaPlayback.track.thumbnail || "") : ""
                        incomingSource: parent.visible && NumaPlayback.crossfadeActive ? (NumaPlayback.transitionTrack.thumbnail || "") : ""
                        transitioning: NumaPlayback.crossfadeActive
                        progress: NumaPlayback.crossfadeProgress
                        dissolve: true
                        motionCoverVisible: compactMotionLoader.active && compactMotionLoader.item !== null
                                            && compactMotionLoader.item.live && compactMotionLoader.opacity >= 1
                    }

                    Loader {
                        id: compactMotionLoader
                        anchors.fill: parent
                        active: parent.visible && popup.hasTrack
                                && NumaAppearance.animatedArtworkEnabled
                                && !NumaAppearance.performanceMode
                                && !!NumaPlayback.animatedArtworkUrl
                        opacity: NumaPlayback.crossfadeActive ? 1 - Math.min(1, NumaPlayback.crossfadeProgress / 0.15) : 1

                        sourceComponent: Component {
                            AnimatedArtwork {
                                radius: 24
                                source: NumaPlayback.animatedArtworkUrl
                                playing: popup.open && !panel.lyricsMode
                                         && NumaPlayback.playing
                            }
                        }
                    }
                }

                LyricsView {
                    anchors.fill: parent
                    visible: panel.lyricsMode
                    running: visible && popup.open
                    pixelSize: 21
                    accentColor: panel.accentColor
                    primaryText: panel.primaryText
                    mutedText: panel.mutedText
                }
            }

            RowLayout {
                Layout.alignment: Qt.AlignHCenter
                spacing: 7

                RoundControl {
                    glyph: "shuffle"; label: NumaPlayback.shuffleEnabled ? qsTr("Shuffle on") : qsTr("Shuffle off")
                    active: NumaPlayback.shuffleEnabled; onClicked: NumaPlayback.shuffleEnabled = !NumaPlayback.shuffleEnabled
                }
                RoundControl {
                    glyph: "skip-back"; label: qsTr("Previous")
                    enabled: NumaPlayback.canGoPrevious && !NumaPlayback.loading; onClicked: NumaPlayback.previous()
                }
                RoundControl {
                    main: true; glyph: NumaPlayback.playing ? "pause" : "play"
                    label: NumaPlayback.playing ? qsTr("Pause") : qsTr("Play")
                    enabled: popup.hasTrack && !NumaPlayback.loading; onClicked: NumaPlayback.toggle()
                }
                RoundControl {
                    glyph: "skip-forward"; label: qsTr("Next")
                    enabled: NumaPlayback.canGoNext && !NumaPlayback.loading; onClicked: NumaPlayback.next()
                }
                RoundControl {
                    glyph: NumaPlayback.repeatMode === "one" ? "repeat-1" : "repeat"; label: qsTr("Repeat")
                    active: NumaPlayback.repeatMode !== "off"; onClicked: NumaPlayback.cycleRepeatMode()
                }
            }

            ColumnLayout {
                Layout.fillWidth: true
                visible: !panel.lyricsMode
                spacing: 0

                Slider {
                    id: progress
                    Layout.fillWidth: true; from: 0; to: Math.max(1, NumaPlayback.displayDuration)
                    value: pressed ? value : NumaPlayback.displayPosition
                    enabled: NumaPlayback.duration > 0 && !NumaPlayback.loading && !NumaPlayback.crossfadeActive
                    hoverEnabled: true; onPressedChanged: if (!pressed && enabled) NumaPlayback.seek(value)
                    background: Rail { control: progress; fill: progress.visualPosition; mixAmount: popup.mixAmount; mixDotColor: popup.mixAccentColor }
                    handle: Knob { control: progress }
                    Behavior on value { enabled: NumaPlayback.crossfadeActive; NumberAnimation { duration: 70; easing.type: Easing.Linear } }
                }

                RowLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: -3
                    Layout.leftMargin: 8
                    Layout.rightMargin: 8

                    Text {
                        text: popup.timeLabel(progress.pressed ? progress.value : NumaPlayback.displayPosition)
                        color: panel.secondaryText
                        font.family: "Inter"; font.pixelSize: 10
                    }

                    Item { Layout.fillWidth: true }

                    Row {
                        id: mixLabel
                        property real sweep: 0
                        visible: opacity > 0.01
                        opacity: popup.mixAmount

                        NumberAnimation on sweep {
                            running: mixLabel.visible && popup.visible
                            from: -2; to: 9; duration: 1800; loops: Animation.Infinite
                        }

                        Repeater {
                            model: qsTr("Mixing").split("")
                            Text {
                                required property string modelData
                                required property int index
                                text: modelData
                                color: panel.primaryText
                                opacity: 0.45 + 0.55 * Math.max(0, 1 - Math.abs(index - mixLabel.sweep) / 2)
                                font.family: "Inter"; font.pixelSize: 10; font.weight: Font.DemiBold; font.letterSpacing: 0.4
                            }
                        }
                    }

                    Item { Layout.fillWidth: true }

                    Row {
                        spacing: 4
                        Text {
                            visible: popup.mixAmount > 0.01 && NumaPlayback.mixDuration > 0
                            text: "(" + popup.timeLabel(NumaPlayback.mixDuration) + ")"
                            color: panel.secondaryText
                            opacity: 0.7 + 0.3 * popup.mixAmount
                            font.family: "Inter"; font.pixelSize: 10; font.weight: Font.Medium
                        }
                        Text {
                            text: NumaPlayback.displayDuration > 0
                                  ? (popup.mixAmount > 0.01
                                     ? "-" + popup.timeLabel(Math.max(0, NumaPlayback.displayDuration - (progress.pressed ? progress.value : NumaPlayback.displayPosition)))
                                     : popup.timeLabel(NumaPlayback.displayDuration))
                                  : "-:--"
                            color: panel.secondaryText
                            font.family: "Inter"; font.pixelSize: 10
                        }
                    }
                }
            }

            RowLayout {
                Layout.fillWidth: true
                visible: !panel.lyricsMode
                spacing: 8

                RoundControl {
                    glyph: popup.volumeLevel <= 0.001 ? "volume-x" : "volume-2"
                    label: qsTr("Volume")
                    onClicked: popup.setVolume(popup.volumeLevel > 0.001 ? 0 : popup.rememberedVolume)
                }

                Slider {
                    id: volume
                    Layout.fillWidth: true
                    from: 0
                    to: 1
                    stepSize: 0.01
                    value: popup.volumeLevel
                    hoverEnabled: true
                    onMoved: popup.setVolume(value)
                    background: Rail { control: volume; fill: volume.visualPosition }
                    handle: Knob { control: volume }
                }
            }
        }

    }

    component RoundControl: Button {
        required property string glyph
        required property string label
        property bool active: false
        property bool main: false

        Accessible.name: label
        implicitWidth: main ? 42 : 34; implicitHeight: implicitWidth
        hoverEnabled: true; focusPolicy: Qt.TabFocus
        background: Rectangle {
            radius: width / 2
            color: parent.main ? (parent.down ? "#e5e2da" : parent.hovered ? "#ffffff" : "#f7f5f0")
                               : parent.active ? (parent.hovered ? "#3dffffff" : "#2effffff")
                                               : parent.hovered ? "#1fffffff" : "transparent"
            border.color: parent.activeFocus ? panel.primaryText : "transparent"
            opacity: parent.enabled ? 1 : 0.4
            scale: parent.main && parent.down ? 0.94 : 1
            Behavior on color { ColorAnimation { duration: 90 } }
            Behavior on scale { NumberAnimation { duration: 90; easing.type: Easing.OutCubic } }
        }
        contentItem: LucideIcon {
            name: parent.glyph
            color: parent.main ? "#121212" : !parent.enabled ? "#6b6f68" : parent.active ? "#ffffff" : panel.primaryText
            implicitWidth: parent.main ? 19 : 16; implicitHeight: implicitWidth
        }
    }

    component Rail: Item {
        id: rail
        required property Slider control
        required property real fill
        property real mixAmount: 0
        property color mixDotColor: panel.accentColor
        implicitHeight: 18

        Rectangle {
            anchors.verticalCenter: parent.verticalCenter
            x: parent.control.leftPadding; width: parent.control.availableWidth
            height: rail.mixAmount > 0.01 ? 6 : rail.control.hovered || rail.control.pressed ? 6 : 4
            radius: height / 2; color: "#2effffff"
            Behavior on height { NumberAnimation { duration: 180; easing.type: Easing.OutBack; easing.overshoot: 2 } }

            MixDotFill {
                anchors.fill: parent
                fraction: Math.max(0, Math.min(1, rail.fill))
                baseColor: Qt.rgba(1, 1, 1, rail.control.hovered || rail.control.pressed ? 1 : 0.75)
                dotColor: rail.mixDotColor
                amount: rail.mixAmount
                maxBand: 160; rows: 2
                layer.enabled: rail.mixAmount > 0.01
                layer.effect: MultiEffect {
                    autoPaddingEnabled: true; shadowEnabled: true
                    shadowColor: rail.mixDotColor; shadowBlur: 0.7
                    shadowOpacity: 0.6 * rail.mixAmount
                    shadowHorizontalOffset: 0; shadowVerticalOffset: 0
                }
            }
        }
    }

    component Knob: Item {
        property Slider control
    }
}
