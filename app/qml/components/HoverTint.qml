/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import QtQuick

// Darken only the selected pink button surfaces without moving their layout.
Item {
    id: root
    objectName: "buttonHoverTint"
    required property Item control
    property bool hovered: false
    readonly property color surfaceColor: paintedColor(control ? control.background : null, 0)
    readonly property bool pink: Qt.colorEqual(surfaceColor, eligibleColor)
    readonly property bool active: NumaAppearance.rosePine && control && control.enabled && hovered && pink
    readonly property color eligibleColor: NumaAppearance.theme === "rose_pine_dawn" ? "#efd4cf" : "#ebbcba"
    enabled: false

    function paintedColor(item, depth) {
        if (!item || depth > 2) return "transparent";
        if (item.color !== undefined && item.color.a > 0.02) return item.color;
        const children = item.children || [];
        for (let i = 0; i < children.length; ++i) {
            const color = paintedColor(children[i], depth + 1);
            if (color.a > 0.02) return color;
        }
        return "transparent";
    }
    Rectangle {
        parent: root.control ? root.control.background : null
        anchors.fill: parent
        radius: root.surfaceRadius(parent, 0)
        color: "#000000"
        opacity: root.active ? 0.12 : 0
        enabled: false
        Behavior on opacity { NumberAnimation { duration: 100 } }
    }
    function surfaceRadius(item, depth) {
        if (!item || depth > 2) return 10;
        if (item.radius !== undefined) return item.radius;
        const children = item.children || [];
        return children.length ? surfaceRadius(children[0], depth + 1) : 10;
    }
}
