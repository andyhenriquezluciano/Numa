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
import QtQuick.Window
import ".."

Item {
    id: root

    property url source: ""
    property var fallbackSources: []
    property int sourceAttempt: 0
    property bool retried: false
    property bool reloading: false
    property Item loadViewport: null
    property string grantedThumbnail: ""
    property bool completed: false
    readonly property string thumbnailCandidate: reloading ? "" : candidates[sourceAttempt] || ""
    readonly property var candidates: {
        const values = [root.sizedSource(root.source), root.source.toString()];
        for (const value of root.fallbackSources) {
            if (value) values.push(root.sizedSource(value), value.toString());
        }
        return values.filter((value, index) => value.length > 0 && values.indexOf(value) === index);
    }
    property real radius: 10
    // Square bottom corners let a card footer sit flush under the cover.
    property real bottomRadius: radius
    // Shared sizes let shelves, search and the player reuse nearby thumbnail sizes.
    readonly property int artworkPixels: {
        const pixels = Math.max(width, height) * Screen.devicePixelRatio * 1.15;
        return [64, 128, 256, 512, 1024, 2048].find(size => size >= pixels) || 2048;
    }
    readonly property alias status: image.status
    readonly property bool hasImage: image.status === Image.Ready

    onThumbnailCandidateChanged: syncThumbnailRequest()
    onLoadViewportChanged: syncThumbnailRequest()
    onVisibleChanged: {
        if (visible && loadViewport && completed && !grantedThumbnail)
            ThumbnailQueue.wake();
    }
    Component.onCompleted: {
        completed = true;
        syncThumbnailRequest();
    }
    Component.onDestruction: {
        if (loadViewport)
            ThumbnailQueue.release(root);
    }

    function syncThumbnailRequest() {
        if (!completed)
            return;
        ThumbnailQueue.release(root);
        grantedThumbnail = "";
        if (loadViewport && thumbnailCandidate)
            ThumbnailQueue.request(root);
    }

    function distanceFrom(viewport) {
        const bounds = mapToItem(viewport, 0, 0, width, height);
        return Math.max(0, -bounds.x - bounds.width, bounds.x - viewport.width)
             + Math.max(0, -bounds.y - bounds.height, bounds.y - viewport.height);
    }

    function thumbnailDistance() {
        const window = root.Window.window;
        return distanceFrom(loadViewport) + (window ? distanceFrom(window.contentItem) : 0);
    }

    onSourceChanged: {
        sourceAttempt = 0;
        retried = false;
        retryTimer.stop();
        reloadDelay.stop();
        reloading = false;
    }

    function sizedSource(value) {
        const url = value.toString();
        // More pixels, fewer witness-protection portraits. Only resize Google's
        // image CDN URLs; other providers may use signed image addresses.
        if (!/^https:\/\/[^/]*(?:googleusercontent\.com|ggpht\.com)\//i.test(url))
            return url;
        return url.replace(/([=-])s\d+(?=[-/?#]|$)/i, "$1s" + artworkPixels)
                  .replace(/([=-])w\d+(?=[-/?#]|$)/i, "$1w" + artworkPixels)
                  .replace(/-h\d+(?=[-/?#]|$)/i, "-h" + artworkPixels);
    }

    function reload() {
        if (root.source.toString().length > 0) {
            reloading = true;
            root.sourceAttempt = 0;
            reloadDelay.restart();
        }
    }

    Timer {
        id: reloadDelay
        interval: 32
        onTriggered: root.reloading = false
    }

    Connections {
        target: NumaNetwork
        function onOfflineChanged() {
            if (!NumaNetwork.offline && root.visible && image.status === Image.Error) {
                root.retried = false;
                root.reload();
            }
        }
    }

    Timer {
        id: retryTimer
        interval: 3000
        repeat: false
        onTriggered: {
            if (root.visible && !root.retried && image.status === Image.Error && !NumaNetwork.offline) {
                root.retried = true;
                root.reload();
            }
        }
    }

    Rectangle {
        anchors.fill: parent
        radius: root.radius
        bottomLeftRadius: root.bottomRadius
        bottomRightRadius: root.bottomRadius
        color: NumaAppearance.rosePine ? NumaAppearance.overlayColor : "#14ffffff"
        antialiasing: true
        visible: image.status === Image.Error || (image.status === Image.Null && !root.thumbnailCandidate)
        LucideIcon {
            anchors.centerIn: parent
            width: Math.min(parent.width, parent.height) * 0.45
            height: width
            name: "music-2"
            color: NumaAppearance.mutedColor
        }

    }

    Image {
        id: image

        anchors.fill: parent
        source: root.loadViewport ? root.grantedThumbnail : root.thumbnailCandidate
        asynchronous: true
        cache: true
        fillMode: Image.PreserveAspectCrop
        sourceSize.width: root.artworkPixels
        sourceSize.height: root.artworkPixels
        smooth: true
        onStatusChanged: {
            if (root.loadViewport && (status === Image.Ready || status === Image.Error))
                ThumbnailQueue.release(root);
            if (status !== Image.Error)
                return;
            if (root.sourceAttempt + 1 < root.candidates.length)
                root.sourceAttempt += 1;
            else if (root.visible && !root.retried && !NumaNetwork.offline)
                retryTimer.start();
        }
        // Sampled by the mask shader; a hidden Image still provides its texture.
        visible: false
    }

    // Rounds corners in one pass so cards need no offscreen layers.
    ShaderEffect {
        readonly property real sourceAspect: image.implicitHeight > 0 ? image.implicitWidth / image.implicitHeight : 1
        readonly property real itemAspect: height > 0 ? width / height : 1
        property var source: image
        property size size: Qt.size(width, height)
        property size cropScale: sourceAspect > itemAspect ? Qt.size(itemAspect / sourceAspect, 1)
                                                           : Qt.size(1, sourceAspect / itemAspect)
        property real radius: root.radius
        property real bottomRadius: root.bottomRadius

        anchors.fill: parent
        visible: root.hasImage
        fragmentShader: "qrc:/shaders/rounded_artwork.frag.qsb"
    }

}
