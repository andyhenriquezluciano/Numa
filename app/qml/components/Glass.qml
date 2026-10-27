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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

import QtQuick
import Numa

// Liquid glass over a backdrop. A backdrop with layer.enabled is already a texture, so every glass
// samples it directly and nothing is rendered twice. Anything else gets a private capture.
Item {
    id: root

    required property Item backdrop
    property real radius: 20
    // Recapture whenever the backdrop repaints; leave off for static backdrops. Layered backdrops are always live.
    property bool live: false
    // Private capture resolution relative to device pixels. The blur hides the difference; the GPU notices it.
    property real captureScale: 0.5
    readonly property bool sharedBackdrop: root.backdrop !== null && root.backdrop.layer.enabled

    property alias blurRadius: lens.blurRadius
    property real bevelDepth: Math.min(18, root.radius)
    property alias refraction: lens.refraction
    property alias chromaticAberration: lens.chromaticAberration
    property real edgeHighlight: 0.35
    property real specular: 0
    property real fresnel: 0.6
    property alias saturation: lens.saturation
    property alias brightness: lens.brightness
    property alias tint: lens.tint
    property alias shadowOpacity: lens.shadowOpacity
    property alias shadowSpread: lens.shadowSpread
    property alias shadowOffsetY: lens.shadowOffsetY

    function refresh() {
        if (!root.sharedBackdrop)
            capture.scheduleUpdate();
    }

    ShaderEffectSource {
        id: capture

        readonly property real scale: Screen.devicePixelRatio * root.captureScale

        // Idle when the backdrop is shared: no source item, no layer, no work.
        sourceItem: root.sharedBackdrop || NumaAppearance.performanceMode ? null : root.backdrop
        sourceRect: lens.backdropRect
        textureSize: Qt.size(Math.max(1, Math.ceil(root.width * scale)), Math.max(1, Math.ceil(root.height * scale)))
        live: false
        recursive: false
        hideSource: false
        visible: false
        Component.onCompleted: scheduleUpdate()
        onSourceItemChanged: scheduleUpdate()
        onSourceRectChanged: scheduleUpdate()
    }

    Timer {
        interval: 66
        repeat: true
        running: root.live && root.visible && !root.sharedBackdrop
                 && !NumaAppearance.performanceMode
                 && !NumaWindowState.backgroundIdle
        onTriggered: capture.scheduleUpdate()
    }

    // Tuned for dark UI under ink washes: frosted, a thin lens at the rim, no specular glare.
    // Upstream defaults are a clear magnifier, which is great until you try to read through it.
    LiquidGlass {
        id: lens

        visible: !NumaAppearance.performanceMode

        anchors.fill: parent
        source: root.sharedBackdrop ? root.backdrop : capture
        backdrop: root.backdrop
        radius: root.radius
        bevelDepth: NumaAppearance.bordersEnabled ? root.bevelDepth : 0
        blurRadius: 24
        refraction: 0.69
        chromaticAberration: 0.05
        edgeHighlight: NumaAppearance.bordersEnabled ? root.edgeHighlight : 0
        specular: NumaAppearance.bordersEnabled ? root.specular : 0
        fresnel: NumaAppearance.bordersEnabled ? root.fresnel : 0
        saturation: 0.25
    }
}
