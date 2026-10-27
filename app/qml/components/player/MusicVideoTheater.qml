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
import QtQuick.Window
import QtQuick.Shapes
import QtMultimedia

// Full-window music video. Controls float over the picture and fade out while it plays.
Rectangle {
    id: root

    readonly property color accentColor: NumaAppearance.textColor
    readonly property real barHeight: 120
    property bool upNextOpen: false
    property bool chromeShown: true
    property int previousWindowVisibility: Window.Maximized
    // One automatic re-resolve per stream; signed URLs expire mid-session.
    property bool retried: false
    property double lastFrameAt: Date.now()
    property double lastSeekAt: 0
    property int pictureRecoveries: 0
    property bool initialPictureSeek: true
    property bool pictureReceived: false
    property double previousAudioPosition: -1
    property bool audioWasPlaying: false
    readonly property var video: videoLoader.item
    property url pictureSource: NumaMusicVideo.source
    onPictureSourceChanged: {
        pictureReceived = false;
        pictureResume.stop();
        pictureRecoveries = 0;
        videoLoader.active = false;
        pictureReload.restart();
    }
    // Controls stay up while something on them is in use or the picture is not playing.
    readonly property bool chromeHeld: upNextOpen || bottomBar.menuOpen || topHover.hovered || bottomHover.hovered
                                      || !NumaPlayback.playing || NumaMusicVideo.status !== "ready"

    color: "#000000"
    focus: true

    Component.onCompleted: {
        const win = root.Window.window;
        if (win)
            previousWindowVisibility = win.visibility;
    }

    Component.onDestruction: {
        if (NumaWindowState.videoFullScreen)
            NumaWindowState.setVideoFullScreen(false, previousWindowVisibility);
    }

    function syncVideo() {
        if (!video || video.mediaStatus === MediaPlayer.NoMedia || video.mediaStatus === MediaPlayer.LoadingMedia
                || video.mediaStatus === MediaPlayer.InvalidMedia)
            return;
        const target = NumaPlayback.audiblePosition * 1000;
        const now = Date.now();
        const buffering = video.mediaStatus === MediaPlayer.BufferingMedia
                       || video.mediaStatus === MediaPlayer.StalledMedia;
        const presented = NumaMusicVideo.presentedPosition();
        const picturePosition = presented >= 0 && root.pictureReceived ? presented * 1000 : video.position;
        const drift = target - picturePosition;
        // Follow the audible clock, not the decoder's queued audio position.
        if (initialPictureSeek && video.seekable) {
            initialPictureSeek = false;
            lastSeekAt = now;
            video.position = Math.max(0, target);
            if (NumaPlayback.playing) {
                video.pause();
                pictureResume.restart();
                return;
            }
        } else if (!buffering && video.seekable && Math.abs(drift) > 600
                && now - lastSeekAt > 3000) {
            lastSeekAt = now;
            video.playbackRate = 1;
            video.position = Math.max(0, target);
        } else if (!buffering && now - lastSeekAt > 750) {
            video.playbackRate = NumaPlayback.playing && Math.abs(drift) > 70
                    ? Math.max(0.94, Math.min(1.06, 1 + drift / 4000)) : 1;
        }
        if (NumaPlayback.playing && !pictureResume.running && video.playbackState !== MediaPlayer.PlayingState)
            video.play();
        else if (!NumaPlayback.playing && video.playbackState === MediaPlayer.PlayingState)
            video.pause();
    }

    function checkPicture() {
        if (!video || !NumaPlayback.playing || !root.visible || NumaWindowState.idle
                || NumaMusicVideo.status !== "ready") {
            lastFrameAt = Date.now();
            return;
        }
        if (Date.now() - lastFrameAt < 15000 || pictureRecoveries >= 2)
            return;
        pictureRecoveries++;
        lastFrameAt = Date.now();
        lastSeekAt = lastFrameAt;
        videoLoader.active = false;
        pictureReload.restart();
    }

    Connections {
        target: NumaMusicVideo
        function onVideoFramePresented() { root.pictureReceived = true; }
    }

    Connections {
        target: NumaPlayback
        function onStateChanged() {
            if (root.audioWasPlaying !== NumaPlayback.playing)
                root.syncVideo();
            root.audioWasPlaying = NumaPlayback.playing;
        }
        function onPositionChanged() {
            const at = NumaPlayback.position;
            const jumped = root.previousAudioPosition >= 0 && Math.abs(at - root.previousAudioPosition) > 2;
            root.previousAudioPosition = at;
            if (jumped && root.video && root.video.seekable) {
                root.lastSeekAt = Date.now();
                root.video.playbackRate = 1;
                root.video.position = Math.max(0, NumaPlayback.audiblePosition * 1000);
            }
            if (jumped)
                root.syncVideo();
        }
    }

    Connections {
        target: picture.output.videoSink
        function onVideoFrameChanged() { root.lastFrameAt = Date.now(); }
    }

    function wake() {
        root.chromeShown = true;
        idleTimer.restart();
    }

    function toggleFullScreen() {
        const win = root.Window.window;
        if (!win)
            return;
        if (NumaWindowState.videoFullScreen) {
            NumaWindowState.setVideoFullScreen(false, previousWindowVisibility);
        } else {
            previousWindowVisibility = win.visibility;
            NumaWindowState.setVideoFullScreen(true, previousWindowVisibility);
        }
        root.wake();
    }

    onChromeHeldChanged: if (!chromeHeld) idleTimer.restart()

    Timer {
        id: idleTimer

        interval: 2500
        onTriggered: if (!root.chromeHeld) root.chromeShown = false
    }

    // Passive, so it sees pointer motion even over the controls.
    HoverHandler {
        property point last: Qt.point(-1, -1)

        cursorShape: root.chromeShown ? Qt.ArrowCursor : Qt.BlankCursor
        onPointChanged: {
            const pos = point.scenePosition;
            // Animated items resend hover under a still cursor; only real movement counts.
            if (Math.abs(pos.x - last.x) < 1 && Math.abs(pos.y - last.y) < 1)
                return;
            last = pos;
            root.wake();
        }
    }

    Shortcut {
        sequence: "Space"
        enabled: root.visible && NumaMusicVideo.open
        autoRepeat: false
        onActivated: { NumaPlayback.toggle(); root.wake(); }
    }

    Shortcut {
        sequences: ["F", "F11"]
        enabled: root.visible && NumaMusicVideo.open
        autoRepeat: false
        onActivated: root.toggleFullScreen()
    }

    Shortcut {
        sequence: "Esc"
        enabled: root.visible && NumaMusicVideo.open
        autoRepeat: false
        onActivated: {
            if (NumaWindowState.videoFullScreen)
                root.toggleFullScreen();
            else
                NumaMusicVideo.hide();
        }
    }

    Timer {
        id: pictureResume
        interval: 100
        onTriggered: {
            if (root.video && NumaPlayback.playing)
                root.video.play();
        }
    }

    Timer {
        id: pictureReload
        interval: 1
        onTriggered: videoLoader.active = root.pictureSource.toString().length > 0
    }

    Loader {
        id: videoLoader
        active: root.pictureSource.toString().length > 0
        sourceComponent: MediaPlayer {

        source: root.pictureSource
        videoOutput: picture.output
        activeAudioTrack: -1
        onSourceChanged: {
            root.retried = false;
            root.lastFrameAt = Date.now();
            root.lastSeekAt = 0;
            root.initialPictureSeek = true;
        }
        onSeekableChanged: if (seekable) Qt.callLater(root.syncVideo)
        onMediaStatusChanged: if (mediaStatus === MediaPlayer.LoadedMedia || mediaStatus === MediaPlayer.BufferedMedia) root.syncVideo()
        onErrorOccurred: {
            if (!root.retried) {
                root.retried = true;
                NumaMusicVideo.retry();
            }
        }
        Component.onCompleted: Qt.callLater(root.syncVideo)
        }
    }

    Timer {
        interval: 1000
        repeat: true
        running: NumaMusicVideo.status === "ready"
        onTriggered: root.checkPicture()
    }

    Timer {
        interval: 250
        repeat: true
        running: NumaMusicVideo.status === "ready"
        onTriggered: root.syncVideo()
    }

    TheaterPicture {
        id: picture

        anchors.fill: parent
        visible: NumaMusicVideo.source.toString().length > 0

        // Single tap waits out the double-tap window so a double click never blips the audio.
        TapHandler {
            onSingleTapped: NumaPlayback.toggle()
            onDoubleTapped: root.toggleFullScreen()
        }
    }

    Rectangle {
        anchors.fill: parent
        visible: !root.pictureReceived && NumaMusicVideo.status !== "error"
                 && NumaMusicVideo.status !== "unavailable"
        color: "#000000"
    }

    Item {
        id: loadingIndicator
        anchors.centerIn: parent
        width: 44
        height: 44
        readonly property bool waiting: NumaPlayback.playing && (NumaMusicVideo.status === "checking" || NumaMusicVideo.status === "loading"
                 || (!root.pictureReceived && NumaMusicVideo.status === "ready")
                 || (NumaMusicVideo.status === "ready" && (!root.video || root.video.mediaStatus === MediaPlayer.LoadingMedia))
                 || (root.video && root.video.mediaStatus === MediaPlayer.StalledMedia))
        visible: waiting && !loadingDelay.running
        onWaitingChanged: if (waiting) loadingDelay.restart()
        Timer { id: loadingDelay; interval: 200; running: loadingIndicator.waiting }
        Rectangle { anchors.fill: parent; radius: 22; color: "transparent"; border.width: 3; border.color: "#25ffffff" }
        Shape {
            anchors.fill: parent
            ShapePath {
                strokeWidth: 3
                strokeColor: root.accentColor
                fillColor: "transparent"
                capStyle: ShapePath.RoundCap
                PathAngleArc { centerX: 22; centerY: 22; radiusX: 20; radiusY: 20; startAngle: -90; sweepAngle: 100 }
            }
            RotationAnimator on rotation {
                from: 0; to: 360; duration: 850; loops: Animation.Infinite
                running: loadingIndicator.visible
            }
        }
    }

    ColumnLayout {
        anchors.centerIn: parent
        visible: NumaMusicVideo.status === "unavailable" || NumaMusicVideo.status === "error"
        spacing: 14

        Label {
            Layout.alignment: Qt.AlignHCenter
            text: NumaMusicVideo.status === "error" ? qsTr("The video couldn't be played.")
                                                       : qsTr("No music video for this song.")
            color: "#f7f5f0"
            font.pixelSize: 18
            font.weight: Font.DemiBold
        }

        Label {
            Layout.alignment: Qt.AlignHCenter
            Layout.maximumWidth: 480
            visible: text.length > 0
            text: NumaMusicVideo.errorMessage
            color: "#8d928a"
            font.pixelSize: 13
            wrapMode: Text.Wrap
            horizontalAlignment: Text.AlignHCenter
        }

        Button {
            id: recoverButton

            Layout.alignment: Qt.AlignHCenter
            text: NumaMusicVideo.status === "error" ? qsTr("Try again") : qsTr("Back to song")
            onClicked: NumaMusicVideo.status === "error" ? NumaMusicVideo.retry() : NumaMusicVideo.hide()
            background: Rectangle { radius: 20; implicitHeight: 40; color: root.accentColor }
            contentItem: Label { text: recoverButton.text; color: "#0b0d0a"; font.pixelSize: 13; font.weight: Font.DemiBold; leftPadding: 18; rightPadding: 18; verticalAlignment: Text.AlignVCenter }
        }
    }

    // Shown even while the controls are hidden, like a player's own skip offer.
    TheaterSkipButton {
        videoBackdrop: picture
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.rightMargin: root.upNextOpen ? 408 : 40
        anchors.bottomMargin: root.chromeShown ? root.barHeight + 24 : 40

        Behavior on anchors.bottomMargin { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
    }

    TheaterUpNext {
        videoBackdrop: picture
        anchors.right: parent.right
        anchors.rightMargin: 16
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.topMargin: root.barHeight
        anchors.bottomMargin: root.barHeight
        width: Math.min(380, parent.width - 32)
        visible: root.upNextOpen
        onCloseRequested: root.upNextOpen = false
    }

    // Upper controls on a scrim that keeps them legible over bright footage.
    Item {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        height: root.barHeight + 40
        opacity: root.chromeShown ? 1 : 0
        visible: opacity > 0.01

        Behavior on opacity { NumberAnimation { duration: 250 } }

        HoverHandler { id: topHover }

        Rectangle {
            anchors.fill: parent
            gradient: Gradient {
                GradientStop { position: 0; color: "#b3000000" }
                GradientStop { position: 1; color: "#00000000" }
            }
        }

        TheaterTopBar {
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.leftMargin: 40
            anchors.rightMargin: 40
            height: root.barHeight
            accentColor: root.accentColor
            upNextOpen: root.upNextOpen
            onUpNextToggled: root.upNextOpen = !root.upNextOpen
        }
    }

    Item {
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: root.barHeight + 60
        opacity: root.chromeShown ? 1 : 0
        visible: opacity > 0.01

        Behavior on opacity { NumberAnimation { duration: 250 } }

        HoverHandler { id: bottomHover }

        Rectangle {
            anchors.fill: parent
            gradient: Gradient {
                GradientStop { position: 0; color: "#00000000" }
                GradientStop { position: 1; color: "#cc000000" }
            }
        }

        TheaterBottomBar {
            id: bottomBar
            onFullscreenRequested: root.toggleFullScreen()
            videoBackdrop: picture

            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            height: root.barHeight
            accentColor: root.accentColor
        }
    }
}
