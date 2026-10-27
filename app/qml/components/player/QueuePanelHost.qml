/*
 * Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import Numa
import QtQuick
import QtQuick.Effects
import QtQuick.Window

Item {
    id: host

    required property Item shell
    required property Item scene
    required property Item player
    readonly property alias item: panelLoader.item
    readonly property real compactPulse: panelLoader.item
                                                 ? panelLoader.item.compactPulse : 0

    Loader {
        id: compactBackground
        anchors.fill: parent
        active: host.shell.compactMode
        sourceComponent: Item {
        id: compactBackdrop
        anchors.fill: parent
        visible: host.shell.compactMode
        layer.enabled: host.shell.compactMode
        layer.effect: MultiEffect {
            maskEnabled: true
            maskSource: compactMask
            maskThresholdMin: 0.5
            maskSpreadAtMin: 1.0
        }

        Rectangle {
            id: compactMask
            anchors.fill: parent
            radius: 20
            color: "white"
            antialiasing: true
            visible: false
        }

        FullscreenBackdrop {
            anchors.fill: parent
            inkColor: host.player.inkColor
            thumbnail: NumaPlayback.track.thumbnail || ""
            reveal: compactBackdrop.visible ? 1 : 0
            split: 0
            playing: compactBackdrop.visible && !NumaWindowState.backgroundIdle && NumaPlayback.playing
            warpRunning: compactBackdrop.visible && NumaPlayback.playing
                         && !NumaWindowState.backgroundIdle
                         && !NumaAppearance.performanceMode
                         && host.Window.window !== null
                         && host.Window.window.visibility !== Window.Minimized
            warpThrottled: NumaWindowState.backgroundIdle
            warpSpeed: NumaAppearance.speed
            warpIntensity: NumaAppearance.intensity
            warpSaturation: NumaAppearance.saturation
            warpBrightness: NumaAppearance.brightness * (1 + 0.12 * host.compactPulse)
        }
    }
    }

    Loader {
        id: panelLoader

        x: host.shell.compactMode ? 0 : host.width - width - 14
        y: host.shell.compactMode ? 0
           : host.shell.canopy ? host.scene.topBar.y + host.scene.topBar.height + 8 : 40
        width: host.shell.compactMode ? host.width : host.scene.queueWidth
        height: host.shell.compactMode ? host.height
            : host.height - y - host.scene.playerDock.height
        z: 7
        active: host.shell.compactMode || host.shell.queueOpen || host.shell.queuePanelRetained
        onLoaded: Qt.callLater(() => {
            if (item)
                item.open = Qt.binding(() => host.shell.compactMode || host.shell.queueOpen);
        })

        sourceComponent: Component {
            QueuePanel {
                compactMode: host.shell.compactMode
                backdrop: host.shell.compactMode ? compactBackground.item : host.scene
                backdropLive: visible && !NumaWindowState.backgroundIdle && !NumaAppearance.performanceMode
                motionArtwork: visible && !NumaWindowState.backgroundIdle && !host.shell.fullscreenOpen
                accentColor: host.player.accentColor
                mixAccentColor: host.player.mixAccentColor
                inkColor: host.player.inkColor
                enabled: host.shell.compactMode || host.shell.queueOpen
                onCloseRequested: host.shell.queueOpen = false
                onCompactExitRequested: host.shell.requestCompactMode(false)
            }
        }
    }
}
