/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 */

import Numa
import QtQuick
import QtQuick.Effects
import QtQuick.Window
import QtQuick.Controls

Item {
    id: root

    property color accentColor: "#d8dcde"
    property real radius: 12
    property bool neutral: true
    property real tintOpacity: 0.38
    property bool refractionEnabled: true
    property bool opaqueInPerformanceMode: true
    function backdropBehind(overlay, item, scene) {
        if (!overlay) return scene;
        let layer = item;
        while (layer && layer.parent !== overlay) layer = layer.parent;
        if (!layer) return scene;
        let ownIndex = -1;
        for (let i = 0; i < overlay.children.length; ++i)
            if (overlay.children[i] === layer) ownIndex = i;
        for (let i = ownIndex - 1; i >= 0; --i) {
            const behind = overlay.children[i];
            if (behind.visible && behind.contentItem !== undefined) return behind;
        }
        return scene;
    }
    property Item backdrop: backdropBehind(root.Overlay.overlay, root.parent,
        root.Window.window && root.Window.window.contentItem.children.length > 0
            ? root.Window.window.contentItem.children[0] : null)

    Rectangle {
        id: bloomCore
        anchors.centerIn: parent
        width: Math.max(18, parent.width * 0.36)
        height: Math.max(18, parent.height - 18)
        radius: Math.min(width, height) / 2
        color: root.accentColor
        visible: false
    }

    MultiEffect {
        anchors.fill: bloomCore
        source: bloomCore
        autoPaddingEnabled: true
        blurEnabled: true
        blur: 0.82
        blurMax: 32
        saturation: root.neutral ? 0 : 0.35
        opacity: root.neutral ? 0.0 : 0.3
        visible: !root.neutral && !NumaAppearance.performanceMode
    }

    Loader {
        anchors.fill: parent
        active: root.backdrop !== null && root.visible && !NumaAppearance.performanceMode
        sourceComponent: Component {
            Glass {
                anchors.fill: parent
                backdrop: root.backdrop
                live: true
                radius: root.radius
                brightness: root.refractionEnabled ? -0.03 : 0
                blurRadius: 24
                bevelDepth: root.refractionEnabled && NumaAppearance.bordersEnabled ? Math.min(18, root.radius) : 0
                refraction: root.refractionEnabled ? 0.22 : 0
                chromaticAberration: 0.0
                edgeHighlight: root.refractionEnabled && NumaAppearance.bordersEnabled ? 0.12 : 0
                fresnel: root.refractionEnabled && NumaAppearance.bordersEnabled ? 0.2 : 0
                specular: 0
                saturation: 0.25
            }
        }
    }

    Rectangle {
        anchors.fill: parent
        radius: root.radius
        color: (root.opaqueInPerformanceMode && NumaAppearance.performanceMode)
            ? NumaAppearance.surfaceColor
            : (root.neutral ? Qt.rgba(0.08, 0.09, 0.10, root.tintOpacity) : "#220e1210")
    }

    Gradient {
        id: surfaceGradient
        orientation: Gradient.Horizontal
        GradientStop {
            position: 0
            color: root.neutral ? "#08ffffff" : Qt.rgba(root.accentColor.r, root.accentColor.g, root.accentColor.b, 0.08)
        }
        GradientStop {
            position: 0.5
            color: "#14ffffff"
        }
        GradientStop {
            position: 1
            color: root.neutral ? "#08ffffff" : Qt.rgba(root.accentColor.r, root.accentColor.g, root.accentColor.b, 0.08)
        }
    }

    Rectangle {
        anchors.fill: parent
        radius: root.radius
        color: "transparent"
        gradient: (root.opaqueInPerformanceMode && NumaAppearance.performanceMode) ? null : surfaceGradient
    }

    RoundedSurfaceFrame {
        anchors.fill: parent
        radius: root.radius
        softGlow: true
        showBorder: true
        fillColor: "transparent"
        border.width: NumaAppearance.borderWidth
        border.color: (root.opaqueInPerformanceMode && NumaAppearance.performanceMode)
            ? NumaAppearance.borderColor
            : "#30ffffff"
    }
}
