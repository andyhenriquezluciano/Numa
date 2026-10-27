/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

import QtQuick
import QtQuick.Controls

ToolTip {
    id: tip

    required property Item control
    property color accentColor: "#7fbe90"

    visible: Boolean(control && (control["hovered"] || control["containsMouse"]))
             && text.length > 0
    delay: 450
    timeout: 5000
    leftPadding: 11
    rightPadding: 11
    topPadding: 7
    bottomPadding: 7

    contentItem: Text {
        text: tip.text
        color: NumaAppearance.textColor
        font.family: "Inter"
        font.pixelSize: 12
        font.weight: Font.Medium
    }

    background: PopupBloomSurface {
        radius: 10
    }
}
