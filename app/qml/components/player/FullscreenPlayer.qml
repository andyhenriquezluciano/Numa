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
import QtQuick.Window

// Full-window now playing. Layout lives in FullscreenPlayerForm.ui.qml; this file binds playback and handles input.
// The form is the stage, this file is the stagehand.
FullscreenPlayerForm {
    id: root

    open: false

    signal closeRequested()
    signal albumRequested(var album)
    signal artistRequested(var artist)
    signal playlistRequested(var playlist)

    readonly property real mixStart: NumaPlayback.mixStart
    readonly property real mixDuration: NumaPlayback.mixDuration
    readonly property string trackId: NumaPlayback.track.id || ""
    property var queueCurrentTrack: ({})
    readonly property bool queueShuffleEnabled: NumaPlayback.shuffleEnabled
    onQueueShuffleEnabledChanged: Qt.callLater(() => queuePane.scrollFollow.orderChanged())

    function showCurrentQueueSong() {
        if (pane === "queue" && queuePane.scrollFollow.following)
            Qt.callLater(() => queuePane.scrollFollow.center());
    }

    function cancelQueueDrag() {
        queuePane.dragIndex = -1;
        queuePane.dropIndex = -1;
    }

    function updateQueueDrop() {
        const view = queuePane.list;
        const position = queuePane.dragY + view.contentY;
        const index = view.indexAt(view.width / 2, position);
        if (index >= 0)
            queuePane.dropIndex = Math.max(0, Math.min(queuePane.queueItems.length - 1, index - queuePane.queueStart));
        else if (queuePane.dragY < 0)
            queuePane.dropIndex = 0;
        else if (position >= view.contentHeight || queuePane.dragY > view.height)
            queuePane.dropIndex = queuePane.queueItems.length - 1;
    }

    Connections {
        target: NumaPlayback
        function onQueueChanged() { root.cancelQueueDrag(); }
    }
    readonly property bool fading: NumaPlayback.crossfadeActive
    property double fadeEndedAt: 0
    property point lastPointer: Qt.point(-1, -1)
    property int visibilityBeforeNativeFullscreen: Window.Maximized
    // Controls stay up while paused, while in use, or with the queue open.
    readonly property bool chromeHeld: !NumaAppearance.fullscreenAutoHide || !NumaPlayback.playing
        || pane === "queue" || controls.hover.hovered || closeButton.hovered || upNext.area.containsMouse
        || mediaMenu.visible || controls.progress.pressed
    // Seconds left before the next song starts mixing in, or the song ends.
    readonly property real handoffIn: (mixStart >= 0 ? mixStart : NumaPlayback.duration) - NumaPlayback.position
    readonly property var nextTrack: upcoming(NumaPlayback.queue, NumaPlayback.repeatMode)

    upNextTrack: nextTrack
    upNextArtist: artistLabel(nextTrack)
    upNextOpen: Boolean(nextTrack.id) && NumaPlayback.playing && !mixing && NumaPlayback.duration > 0
        && handoffIn > 0 && handoffIn <= 15
    pulse: bassPulse.level
    reactive: bassPulse.live
    currentTrack: NumaPlayback.track
    transitionTrack: NumaPlayback.transitionTrack
    position: NumaPlayback.position
    duration: NumaPlayback.duration
    transitionPosition: NumaPlayback.transitionPosition
    transitionDuration: NumaPlayback.transitionDuration
    playing: NumaPlayback.playing
    crossfadeActive: NumaPlayback.crossfadeActive
    crossfadeProgress: NumaPlayback.crossfadeProgress
    queueOriginTitle: String((NumaPlayback.track.queueOrigin || ({})).title || "")
    animatedArtworkEnabled: NumaAppearance.animatedArtworkEnabled
    animatedArtworkUrl: NumaPlayback.animatedArtworkUrl
    canvasLoop: /\.cnvs\.mp4/.test(NumaPlayback.animatedArtworkUrl || "")

    // Qobuz songs show their tier instead of kbps. Untyped for qmlcachegen's sake.
    function sourceQuality(track) {
        if (!track || track.playbackSource !== "qobuz")
            return "";
        const parts = [track.hires ? qsTr("Hi-Res") : qsTr("Lossless")];
        if (track.bitDepth || track.sampleRate) {
            const depth = track.bitDepth ? qsTr("%1-bit").arg(track.bitDepth) : "";
            const rate = track.sampleRate ? qsTr("%1 kHz").arg(Number((track.sampleRate / 1000).toFixed(1))) : "";
            parts.push([depth, rate].filter(Boolean).join(" / "));
        }
        if (track.streamCodec)
            parts.push(track.streamCodec);
        return parts.join(" · ");
    }

    // Streamed songs: bitrate then codec, e.g. "128 kbps · Opus". Needs the bitrate setting.
    function streamQuality(track) {
        if (!NumaAppearance.showBitrate || NumaPlayback.bitrate <= 0)
            return "";
        const kbps = Math.round(NumaPlayback.bitrate >= 1000 ? NumaPlayback.bitrate / 1000 : NumaPlayback.bitrate);
        return track && track.streamCodec ? kbps + " kbps · " + track.streamCodec : kbps + " kbps";
    }

    // Untyped so qmlcachegen leaves the QVariantList lookup interpreted.
    function upcoming(queue, repeatMode) {
        if (repeatMode === "one" || !queue || !queue.length)
            return {};
        return queue[0] || {};
    }

    function artistLabel(track) {
        return (track.artists || []).join(", ") || track.artist || "";
    }

    function timeLabel(seconds) {
        if (!Number.isFinite(seconds) || seconds < 0)
            return "0:00";
        const total = Math.floor(seconds);
        return Math.floor(total / 60) + ":" + String(total % 60).padStart(2, "0");
    }

    function togglePane(name) {
        pane = pane === name ? "" : name;
    }

    function toggleNativeFullscreen() {
        const window = root.Window.window;
        if (!window)
            return;
        if (window.visibility === Window.FullScreen) {
            root.restoreWindowVisibility();
            return;
        }
        root.visibilityBeforeNativeFullscreen = window.visibility;
        if (typeof window.requestFullscreenVisibility === "function")
            window.requestFullscreenVisibility(Window.FullScreen);
        else
            window.showFullScreen();
    }

    function restoreWindowVisibility() {
        const window = root.Window.window;
        if (!window || window.visibility !== Window.FullScreen)
            return;
        if (typeof window.requestFullscreenVisibility === "function")
            window.requestFullscreenVisibility(Window.Maximized);
        else
            window.showMaximized();
    }

    // Shows the controls and restarts the idle countdown.
    function wake() {
        chromeShown = true;
        idleTimer.restart();
    }

    onOpenChanged: {
        if (!open) {
            root.restoreWindowVisibility();
            return;
        }
        queueCurrentTrack = NumaPlayback.track;
        showCurrentQueueSong();
        const window = root.Window.window;
        if (window && window.visibility !== Window.FullScreen)
            root.visibilityBeforeNativeFullscreen = window.visibility;
        forceActiveFocus();
        wake();
    }
    onChromeHeldChanged: wake()
    Keys.onPressed: wake()

    // Swaps lyrics and queue without collapsing the split.
    onPaneChanged: {
        showCurrentQueueSong();
        if (pane === "" || pane === shownPane)
            return;
        if (split < 0.01) {
            shownPane = pane;
            paneSwapIn.restart();
        } else {
            paneSwapAnim.restart();
        }
    }

    onCanvasLiveChanged: if (canvasLive && canvasItem.videoAspect > 0) canvasAspect = Math.max(0.5, Math.min(1, canvasItem.videoAspect))

    onFadingChanged: if (!fading) fadeEndedAt = Date.now()
    onTrackIdChanged: {
        queueCurrentTrack = NumaPlayback.track;
        showCurrentQueueSong();
        if (!visible || fading || Date.now() - fadeEndedAt < 500)
            return;
        bumpAnimation.restart();
    }

    Timer {
        id: idleTimer
        interval: 3000
        onTriggered: if (!root.chromeHeld && root.open) root.chromeShown = false
    }

    BassPulse {
        id: bassPulse
        engine: NumaPlayback.audioEngine
        running: NumaAppearance.fullscreenPulse && !NumaAppearance.performanceMode && root.visible && NumaPlayback.playing
    }

    // Keep the display awake while the fullscreen player is playing.
    Binding {
        target: NumaScreenWake
        property: "active"
        value: root.open && NumaPlayback.playing
    }

    MediaMenu {
        id: mediaMenu
        media: NumaPlayback.track
        onAlbumRequested: function(album) { root.albumRequested(album); }
        onArtistRequested: function(artist) { root.artistRequested(artist); }
        onPlaylistRequested: function(playlist) { root.playlistRequested(playlist); }
    }

    SequentialAnimation {
        id: paneSwapAnim
        NumberAnimation { target: root; property: "paneSwap"; to: 0; duration: 140; easing.type: Easing.InCubic }
        ScriptAction { script: root.shownPane = root.pane }
        NumberAnimation { target: root; property: "paneSwap"; to: 1; duration: 380; easing.type: Easing.OutCubic }
    }

    NumberAnimation { id: paneSwapIn; target: root; property: "paneSwap"; from: 0; to: 1; duration: 520; easing.type: Easing.OutCubic }

    blocker.onWheel: function(wheel) { wheel.accepted = true; }

    // Animated items resend hover under a still cursor; only real movement counts.
    pointer.onPointChanged: {
        const pos = pointer.point.scenePosition;
        if (Math.abs(pos.x - lastPointer.x) < 1 && Math.abs(pos.y - lastPointer.y) < 1)
            return;
        lastPointer = pos;
        wake();
    }

    upNext.area.onClicked: NumaPlayback.next()

    dragHandler.enabled: root.Window.window !== null && root.Window.window.visibility !== Window.FullScreen
    dragHandler.onActiveChanged: if (dragHandler.active) root.Window.window.startSystemMove()

    backdrop.warpThrottled: NumaWindowState.backgroundIdle
    backdrop.warpRunning: !NumaAppearance.performanceMode && root.visible && NumaPlayback.playing && root.Window.window !== null && root.Window.window.visibility !== Window.Minimized
    backdrop.warpSpeed: NumaAppearance.performanceMode ? 0 : NumaAppearance.speed
    backdrop.warpIntensity: NumaAppearance.intensity
    backdrop.warpSaturation: NumaAppearance.saturation
    backdrop.warpBrightness: NumaAppearance.brightness * (1 + 0.12 * root.pulse)

    closeButton.onClicked: root.closeRequested()

    artArea.onClicked: function(mouse) {
        if (mouse.button === Qt.RightButton)
            mediaMenu.popup(root.artArea, mouse.x, mouse.y);
        else
            NumaPlayback.toggle();
    }

    queuePane.queueItems: NumaPlayback.queue || []
    queuePane.continuous: NumaPlayback.queueLayout === "continuous"
    queuePane.historyItems: NumaPlayback.history || []
    queuePane.currentTrack: root.queueCurrentTrack
    queuePane.onContinuousChanged: root.showCurrentQueueSong()
    queuePane.onDragStarted: function(index, position) {
        queuePane.dragIndex = index;
        queuePane.dropIndex = index;
        queuePane.dragY = position;
        root.updateQueueDrop();
    }
    queuePane.onDragMoved: function(position) {
        if (queuePane.dragIndex < 0) return;
        queuePane.dragY = position;
        root.updateQueueDrop();
    }
    queuePane.onDragFinished: {
        const from = queuePane.dragIndex;
        const to = queuePane.dropIndex;
        queuePane.scrollFollow.preserve();
        root.cancelQueueDrag();
        if (from >= 0 && to >= 0) NumaPlayback.moveQueueItem(from, to);
        queuePane.scrollFollow.restore();
    }
    queuePane.onDragCanceled: root.cancelQueueDrag()
    queuePane.onScrollDrag: {
        const view = queuePane.list;
        const delta = queuePane.dragY < 30 ? -12 : queuePane.dragY > view.height - 30 ? 12 : 0;
        view.contentY = Math.max(view.originY, Math.min(Math.max(view.originY, view.originY + view.contentHeight - view.height), view.contentY + delta));
        root.updateQueueDrop();
    }
    queuePane.autoplayEnabled: NumaPlayback.autoplayEnabled
    queuePane.clearButton.onClicked: NumaPlayback.clearQueue()
    queuePane.list.delegate: FullscreenQueueRow {
        required property string entryJson
        required property int index
        width: root.queuePane.list.width - 8
        track: JSON.parse(entryJson)
        rowIndex: index
        dragPane: root.queuePane
        queueIndex: index - root.queuePane.queueStart
        previous: index < root.queuePane.historyCount
        current: root.queuePane.hasCurrent && index === root.queuePane.historyCount
        sectionLabel: !root.queuePane.continuous ? "" : previous && index === 0 ? qsTr("PLAYED")
            : current ? qsTr("NOW PLAYING") : index === root.queuePane.queueStart ? qsTr("UP NEXT") : ""
        artistText: root.artistLabel(track)
        paneSwap: root.paneSwap
        accentColor: root.accentColor
        primaryText: root.primaryText
        mutedText: root.mutedText
        onClicked: {
            if (previous) NumaPlayback.playHistoryIndex(root.queuePane.historyOffset + index);
            else if (!current) NumaPlayback.playQueueIndex(queueIndex);
        }
        removeButton.onClicked: if (queueIndex >= 0) NumaPlayback.removeFromQueue(queueIndex)
    }

    controls.seekLimit: mixStart >= 0 ? Math.max(0, mixStart - 5, NumaPlayback.position) : NumaPlayback.duration
    controls.seekEnabled: NumaPlayback.duration > 0 && !NumaPlayback.loading && !NumaPlayback.errorMessage && !root.mixing
    controls.positionText: timeLabel(controls.scrubbing ? controls.scrubPosition : root.shownPosition)
    controls.remainingText: root.shownDuration > 0
        ? "-" + timeLabel(root.shownDuration - (controls.scrubbing ? controls.scrubPosition : root.shownPosition)) : "-:--"
    controls.mixDurationText: mixDuration > 0 ? timeLabel(mixDuration) : ""
    controls.qualityText: sourceQuality(NumaPlayback.track) || streamQuality(NumaPlayback.track)
    property real rememberedVolume: NumaHome.volume > 0.001 ? NumaHome.volume : 1
    controls.onVolumeChanged: if (controls.volume > 0.001) root.rememberedVolume = controls.volume
    controls.volumeButton.onClicked: NumaHome.volume = NumaHome.volume > 0.001 ? 0 : root.rememberedVolume
    controls.volume: NumaHome.volume

    controls.progress.onPressedChanged: if (!controls.progress.pressed && controls.progress.enabled) NumaPlayback.seek(controls.scrubPosition)
    controls.volumeSlider.onMoved: NumaHome.volume = controls.volumeSlider.value
    controls.volumeWheel.onWheel: function(event) {
        const delta = event.angleDelta.y !== 0 ? event.angleDelta.y : -event.angleDelta.x;
        const step = (event.inverted ? -delta : delta) / 120 * 0.05;
        NumaHome.volume = Math.max(0, Math.min(1, NumaHome.volume + step));
    }

    controls.mixLetters: qsTr("Mixing").split("")
    controls.currentInfo.artistText: artistLabel(NumaPlayback.track)
    controls.incomingInfo.artistText: artistLabel(NumaPlayback.transitionTrack)
    controls.currentInfo.interactive: true
    controls.currentInfo.albumLinked: Boolean(mediaMenu.albumId)
    controls.currentInfo.artistLinked: mediaMenu.artistIds.length > 0
    controls.currentInfo.titleArea.onClicked: function(mouse) {
        if (mouse.button === Qt.RightButton)
            mediaMenu.popup(controls.currentInfo.titleArea, mouse.x, mouse.y);
        else
            mediaMenu.viewAlbum();
    }
    controls.currentInfo.artistArea.onClicked: function(mouse) {
        if (mouse.button === Qt.RightButton)
            mediaMenu.popup(controls.currentInfo.artistArea, mouse.x, mouse.y);
        else
            mediaMenu.viewArtist(controls.currentInfo.artistArea, mouse.x, mouse.y);
    }
    controls.currentInfo.albumArea.onClicked: function(mouse) {
        if (mouse.button === Qt.RightButton)
            mediaMenu.popup(controls.currentInfo.albumArea, mouse.x, mouse.y);
        else
            mediaMenu.viewAlbum();
    }

    controls.moreButton.enabled: Boolean(NumaPlayback.track.id)
    controls.moreButton.onClicked: mediaMenu.popup(controls.moreButton, 0, controls.moreButton.height)

    controls.shuffleButton.modeActive: NumaPlayback.shuffleEnabled
    controls.shuffleButton.tooltip: NumaPlayback.shuffleEnabled ? qsTr("Shuffle on") : qsTr("Shuffle off")
    controls.shuffleButton.onClicked: NumaPlayback.shuffleEnabled = !NumaPlayback.shuffleEnabled

    controls.previousButton.enabled: NumaPlayback.canGoPrevious
    controls.previousButton.tooltip: qsTr("Previous")
    controls.previousButton.onClicked: {
        controls.previousButton.nudgeAnimation.restart();
        NumaPlayback.previous();
    }

    controls.playButton.enabled: !NumaPlayback.loading && Boolean(NumaPlayback.track.id)
    controls.playButton.altShown: NumaPlayback.playing
    controls.playButton.tooltip: NumaPlayback.playing ? qsTr("Pause") : qsTr("Play")
    controls.playButton.onClicked: NumaPlayback.toggle()

    controls.nextButton.enabled: NumaPlayback.canGoNext
    controls.nextButton.tooltip: qsTr("Next")
    controls.nextButton.onClicked: {
        controls.nextButton.nudgeAnimation.restart();
        NumaPlayback.next();
    }

    controls.repeatButton.glyph: NumaPlayback.repeatMode === "one" ? "repeat-1" : "repeat"
    controls.repeatButton.modeActive: NumaPlayback.repeatMode !== "off"
    controls.repeatButton.tooltip: NumaPlayback.repeatMode === "one" ? qsTr("Repeat one")
        : NumaPlayback.repeatMode === "all" ? qsTr("Repeat all") : qsTr("Repeat off")
    controls.repeatButton.onClicked: NumaPlayback.cycleRepeatMode()

    controls.lyricsButton.onClicked: root.togglePane("lyrics")
    controls.videoButton.visible: NumaMusicVideo.status !== "" && NumaMusicVideo.status !== "unavailable"
    controls.videoButton.enabled: NumaMusicVideo.available
    controls.videoButton.onClicked: NumaMusicVideo.show()
    controls.windowFullScreen: root.Window.window !== null && root.Window.window.visibility === Window.FullScreen
    controls.fullScreenButton.onClicked: root.toggleNativeFullscreen()
    controls.queueButton.onClicked: root.togglePane("queue")
}
