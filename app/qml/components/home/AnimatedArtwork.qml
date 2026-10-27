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
import QtMultimedia

// Because static album covers are so 2010. If the artwork isn't gently undulating,
// are you even truly experiencing the sonic landscape?
Item {
    id: root

    property url source: ""
    property real radius: 0
    property real bottomRadius: radius
    property bool playing: true
    readonly property bool effectsPlaying: playing
    property int fillMode: VideoOutput.PreserveAspectCrop

    readonly property bool hasVideo: Boolean(source && source.toString().length > 0)
    // Local copy of source; empty while it downloads.
    property string playableSource: ""
    // True once frames are on screen, so hosts can reshape around the loop without flashing the cover.
    readonly property bool live: videoOutput.visible && videoOutput.sourceRect.height > 0
    readonly property real videoAspect: live ? videoOutput.sourceRect.width / videoOutput.sourceRect.height : 0

    MediaPlayer {
        id: player

        source: root.playableSource
        videoOutput: videoOutput
        audioOutput: null // Keep it strictly silent; the music stream handles the auditory pleasure.
        loops: MediaPlayer.Infinite
        // A loop that won't decode is dropped from both caches so the next play asks the mirrors.
        onErrorOccurred: function(error, errorString) {
            if (error !== MediaPlayer.NetworkError && root.hasVideo) {
                NumaArtworkVideos.discard(root.source.toString());
                NumaAnimatedArtwork.forgetUrl(root.source.toString());
            }
        }
    }

    function resolvePlayable() {
        playableSource = hasVideo ? NumaArtworkVideos.localUrl(source.toString()) : "";
        if (playableSource.length > 0 && effectsPlaying)
            player.play();
        else if (playableSource.length === 0)
            player.stop();
    }

    Component.onCompleted: resolvePlayable()

    Connections {
        target: NumaArtworkVideos

        function onReady(remote, local) {
            if (root.hasVideo && remote === root.source.toString())
                root.resolvePlayable();
        }

        // Only content errors mean a stale URL; going offline must not wipe good entries.
        function onFailed(remote, permanent) {
            if (permanent && root.hasVideo && remote === root.source.toString())
                NumaAnimatedArtwork.forgetUrl(remote);
        }
    }

    Connections {
        target: root

        function onPlayingChanged() {
            if (root.playableSource.length === 0) return;
            if (root.effectsPlaying) {
                player.play();
            } else {
                player.pause();
            }
        }

        function onEffectsPlayingChanged() {
            if (root.playableSource.length === 0) return;
            if (root.effectsPlaying) player.play(); else player.pause();
        }

        function onSourceChanged() {
            root.resolvePlayable();
        }
    }

    VideoOutput {
        id: videoOutput

        anchors.fill: parent
        fillMode: root.fillMode
        visible: root.hasVideo && player.playbackState !== MediaPlayer.StoppedState
        layer.enabled: root.radius > 0
        layer.smooth: true
        layer.effect: ShaderEffect {
            property var source
            property size size: Qt.size(videoOutput.width, videoOutput.height)
            property size cropScale: Qt.size(1, 1)
            property real radius: root.radius
            property real bottomRadius: root.bottomRadius

            fragmentShader: "qrc:/shaders/rounded_artwork.frag.qsb"
        }
    }
}
