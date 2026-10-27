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
import QtQuick.Effects
import QtMultimedia

// Video scaled so its picture, minus black bars baked into the file, fills this item.
Item {
    id: root

    readonly property alias output: output
    readonly property real frameWidth: output.sourceRect.width
    readonly property real frameHeight: output.sourceRect.height
    readonly property bool known: frameWidth > 0 && frameHeight > 0
    // Content rect in 0..1 frame coordinates, fixed for the current video.
    property real cropX: NumaMusicVideo.contentRect.x
    property real cropY: NumaMusicVideo.contentRect.y
    property real cropWidth: NumaMusicVideo.contentRect.width
    property real cropHeight: NumaMusicVideo.contentRect.height
    readonly property real zoom: known
        ? Math.min(width / (frameWidth * cropWidth), height / (frameHeight * cropHeight)) : 1
    readonly property bool hasMargins: known && (frameWidth * cropWidth * zoom < width - 2
                                                || frameHeight * cropHeight * zoom < height - 2)

    clip: true

    Loader {
        anchors.fill: parent
        active: root.visible && root.hasMargins
        sourceComponent: Item {
            Item {
                id: ambient
                anchors.centerIn: parent
                width: 256
                height: Math.max(1, width * root.frameHeight * root.cropHeight
                                    / (root.frameWidth * root.cropWidth))
                scale: Math.max(root.width / width, root.height / height) * 1.08

                // Reuse the decoded picture; only the ambient texture is downsampled.
                ShaderEffectSource {
                    id: ambientSource
                    anchors.fill: parent
                    sourceItem: output
                    sourceRect: Qt.rect(output.width * root.cropX, output.height * root.cropY,
                                        output.width * root.cropWidth, output.height * root.cropHeight)
                    textureSize: Qt.size(256, Math.min(512, Math.max(64, Math.ceil(ambient.height))))
                    live: !NumaWindowState.idle
                    hideSource: false
                    visible: false
                }

                MultiEffect {
                    anchors.fill: parent
                    source: ambientSource
                    autoPaddingEnabled: false
                    blurEnabled: true
                    blurMax: 32
                    blur: 1
                    saturation: -0.12
                    brightness: -0.15
                }
            }
        }
    }

    Item {
        id: pictureViewport
        anchors.centerIn: parent
        width: root.known ? root.frameWidth * root.cropWidth * root.zoom : root.width
        height: root.known ? root.frameHeight * root.cropHeight * root.zoom : root.height
        // Embedded black bars must not cover the ambient margins outside the picture.
        clip: true

        VideoOutput {
            id: output
            endOfStreamPolicy: VideoOutput.KeepLastFrame

            fillMode: root.known ? VideoOutput.Stretch : VideoOutput.PreserveAspectFit
            width: root.known ? root.frameWidth * root.zoom : root.width
            height: root.known ? root.frameHeight * root.zoom : root.height
            x: root.known ? -root.frameWidth * root.cropX * root.zoom : 0
            y: root.known ? -root.frameHeight * root.cropY * root.zoom : 0
        }
    }

    Component.onCompleted: NumaMusicVideo.watchFrames(output.videoSink)
    Component.onDestruction: NumaMusicVideo.watchFrames(null)
}
