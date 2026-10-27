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

import QtQuick
import QtQuick.Controls
import QtQuick.Effects
import QtQuick.Window
import Numa
import "components"
import "components/local"
import "components/player"

Window {
    id: mainWindow

    property bool troubleModalOpen: false
    property bool modeTransitionActive: false
    property bool pendingCompactMode: false
    readonly property bool compactActive: homeShell.item !== null && homeShell.item.compactMode
    readonly property bool fillsScreen: visibility === Window.Maximized || visibility === Window.FullScreen || NumaWindowState.videoFullScreen
    readonly property bool nativeFullScreen: visibility === Window.FullScreen
    // Height the floating player overlaps at the bottom of scrolling pages.
    property real playerInset: 0

    function toggleMaximized() {
        if (mainWindow.visibility === Window.Maximized)
            mainWindow.showNormal();
        else
            mainWindow.showMaximized();
    }

    function setCompactMode(compact) {
        NumaWindowState.setCompactFrame(compact);
        if (compact)
            NumaWindowState.enterCompactMode(420, 680);
        else
            NumaWindowState.exitCompactMode();
    }

    function requestCompactMode(compact) {
        if (modeTransitionActive || compact === compactActive)
            return;
        pendingCompactMode = compact;
        modeTransition.restart();
    }

    SequentialAnimation {
        id: modeTransition
        ScriptAction { script: mainWindow.modeTransitionActive = true }
        NumberAnimation {
            target: mainWindow
            property: "opacity"
            to: 0
            duration: 70
            easing.type: Easing.InOutQuad
        }
        ScriptAction {
            script: {
                homeShell.item.compactMode = mainWindow.pendingCompactMode;
                mainWindow.setCompactMode(mainWindow.pendingCompactMode);
            }
        }
        NumberAnimation {
            target: mainWindow
            property: "opacity"
            to: 1
            duration: 110
            easing.type: Easing.OutQuad
        }
        ScriptAction { script: mainWindow.modeTransitionActive = false }
    }

    visible: true
    width: NumaWindowState.initialWidth
    height: NumaWindowState.initialHeight
    minimumWidth: homeShell.item && homeShell.item.compactMode ? 360 : 900
    minimumHeight: homeShell.item && homeShell.item.compactMode ? 520 : 600
    title: qsTr("NÜMA")
    Component.onCompleted: mainWindow.showMaximized()
    color: "transparent"
    flags: Qt.Window | Qt.FramelessWindowHint
    onClosing: close => {
        // Closing to the tray keeps the music going; the tray menu holds the real exit.
        if (NumaTray.available && NumaTray.closeToTray && NumaAuth.isSignedIn) {
            close.accepted = false;
            mainWindow.hide();
            return;
        }
        Qt.quit();
    }

    Item {
        id: windowRoot
        anchors.fill: parent

        Rectangle {
            id: windowSurface
            anchors.fill: parent
            z: -200
            radius: mainWindow.fillsScreen ? 0 : 8
            color: NumaAppearance.baseColor
            antialiasing: true
            visible: !mainWindow.compactActive
        }

        RoundedSurfaceFrame {
            id: windowBorder
            anchors.fill: parent
            z: 9
            radius: mainWindow.fillsScreen ? 0 : 8
            showBorder: !mainWindow.fillsScreen && !mainWindow.compactActive
            softGlow: false
            fillColor: "transparent"
            visible: !mainWindow.fillsScreen && !mainWindow.compactActive
        }

        Rectangle {
            id: windowSheen
            anchors.fill: parent
            z: 9
            radius: mainWindow.fillsScreen ? 0 : 8
            antialiasing: true
            gradient: Gradient {
                GradientStop { position: 0; color: "#14ffffff" }
                GradientStop { position: 0.25; color: "transparent" }
            }
            visible: !mainWindow.fillsScreen && !mainWindow.compactActive
        }

        Item {
            id: meshBackgroundContainer
            anchors.fill: parent
            z: -100
            visible: !NumaAppearance.rosePine && !mainWindow.compactActive && !NumaMusicVideo.open
                     && !(homeShell.visible && homeShell.item && homeShell.item.immersiveReady)
            layer.enabled: !mainWindow.fillsScreen
            layer.effect: MultiEffect {
                maskEnabled: true
                maskSource: meshMask
                maskThresholdMin: 0.5
                maskSpreadAtMin: 1.0
            }

            Rectangle {
                id: meshMask
                anchors.fill: parent
                radius: 8
                color: "white"
                antialiasing: true
                visible: false
            }

            ShaderEffect {
                id: meshBackground
                anchors.fill: parent

                property real time: 0.0
                property vector2d extent: Qt.vector2d(width, height)

                // One frame per second while idle.
                Timer {
                    running: meshBackgroundContainer.visible && NumaWindowState.idle && mainWindow.visible && !NumaAppearance.performanceMode
                    interval: 1000
                    repeat: true
                    onTriggered: meshBackground.time = (meshBackground.time + 1.0) % 1000.0
                }

                NumberAnimation on time {
                    running: meshBackgroundContainer.visible && !NumaWindowState.idle && mainWindow.visible && !NumaAppearance.performanceMode
                    loops: Animation.Infinite
                    from: 0.0
                    to: 1000.0
                    duration: 1000000
                }

                fragmentShader: "qrc:/shaders/mesh.frag.qsb"
            }
        }

    Item {
        id: titleBar

        anchors.top: parent.top
        anchors.left: parent.left
        anchors.right: parent.right
        height: 38
        // Stack below contentArea itself so its controls receive presses first.
        z: -1

        DragHandler {
            id: windowDragHandler

            target: null
            // Never steal a press already owned by a button or another control.
            grabPermissions: PointerHandler.ApprovesTakeOverByAnything
            onActiveChanged: {
                if (active)
                    mainWindow.startSystemMove();
            }
        }

        TapHandler {
            acceptedButtons: Qt.LeftButton
            gesturePolicy: TapHandler.DragThreshold
            // Two taps: the traditional shortcut for making the numa roomy.
            onDoubleTapped: mainWindow.toggleMaximized()
        }
    }

    WindowControls {
        id: windowControls

        anchors.right: parent.right
        anchors.rightMargin: !mainWindow.fillsScreen ? 6 : 0
        anchors.top: parent.top
        anchors.topMargin: !mainWindow.fillsScreen ? 4 : 0
        targetWindow: mainWindow
        z: 10
        visible: !mainWindow.compactActive && !NumaWindowState.videoFullScreen && reveal > 0.01
        autoHide: mainWindow.visibility === Window.FullScreen && homeShell.item !== null && homeShell.item.fullscreenOpen
    }

    Item {
        id: contentArea

        anchors.fill: parent

        // Passive, so it sees pointer motion even over controls that take hover themselves.
        // Animated items resend hover under a still cursor, so only real movement counts.
        HoverHandler {
            property point last: Qt.point(-1, -1)
            onPointChanged: {
                const pos = point.scenePosition;
                if (Math.abs(pos.x - last.x) < 1 && Math.abs(pos.y - last.y) < 1)
                    return;
                last = pos;
                if (windowControls.autoHide)
                    windowControls.wake();
            }
        }

        LoginView {
            id: loginView

            visible: !NumaAuth.isSignedIn && !NumaAuth.authWebViewActive && !NumaAuth.profileLoading
            onLoginRequested: NumaAuth.startLogin()
            onTroubleRequested: mainWindow.troubleModalOpen = true
        }

        Loader {
            id: homeShell

            anchors.fill: parent
            asynchronous: true
            visible: active && NumaAuth.isSignedIn && NumaAuth.status !== "starting"
            sourceComponent: Component { HomeShell {} }
            onLoaded: {
                item.compactMode = false;
                Qt.callLater(mainWindow.showMaximized);
            }
        }

        Connections {
            target: homeShell.item
            function onCompactModeChanged() {
                if (!mainWindow.modeTransitionActive)
                    mainWindow.setCompactMode(homeShell.item.compactMode);
            }
        }

        Column {
            anchors.centerIn: parent
            spacing: 12
            visible: NumaAuth.profileLoading && !NumaAuth.authWebViewActive

            BusyIndicator {
                anchors.horizontalCenter: parent.horizontalCenter
                running: parent.visible
            }

            Text {
                text: qsTr("Loading your YouTube profile…")
                color: "#9299a3"
                font.pixelSize: 13
                font.family: "Inter"
            }
        }

        Loader {
            anchors.fill: parent
            active: NumaMusicVideo.open && homeShell.visible
            sourceComponent: Component { MusicVideoTheater {} }
        }
    }
    }

    // Owns the file dialogs behind "Add local files", covers and lyrics.
    LocalPickerHost {}

    // Above everything, including window controls, so edges stay grabbable.
    WindowResizeHandles {
        targetWindow: mainWindow
        z: 100
        visible: !mainWindow.fillsScreen
    }
}
