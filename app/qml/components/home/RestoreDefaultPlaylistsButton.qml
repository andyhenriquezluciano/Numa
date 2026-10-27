/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
import QtQuick
import QtQuick.Controls
import ".."

Button {
    id: root
    property bool compact: false
    readonly property var sync: typeof NumaSync === "undefined" ? null : NumaSync
    text: qsTr("Restore default playlists")
    height: compact ? 38 : 48
    padding: 10
    hoverEnabled: true
    enabled: sync && sync.revision >= 0 && (sync.isHiddenPlaylist("LM") || sync.isHiddenPlaylist("SE"))
    Accessible.name: text
    onClicked: {
        sync.restoreDefaultPlaylists()
        if (!NumaNetwork.offline) { NumaHome.refreshLibrary(); NumaHome.refresh() }
    }
    background: Item {
        Rectangle { anchors.fill: parent; radius: 8; color: root.hovered ? "#1cffffff" : "#10ffffff" }
        RoundedSurfaceFrame {
            anchors.fill: parent
            radius: 8
            softGlow: true
            showBorder: true
            border.color: root.activeFocus ? NumaAppearance.accentColor : root.hovered ? "#45ffffff" : "#30ffffff"
            fillColor: "transparent"
        }
    }
    contentItem: Row {
        spacing: 10
        LucideIcon { anchors.verticalCenter: parent.verticalCenter; width: 16; height: 16; name: "history"; color: NumaAppearance.textColor }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            width: Math.max(0, root.availableWidth - 26)
            visible: !root.compact
            text: root.text
            color: NumaAppearance.textColor
            font.family: "Inter"
            font.pixelSize: 11
            wrapMode: Text.WordWrap
        }
    }
    GlassToolTip { control: root; text: qsTr("Show Liked Music and Episodes for Later again.") }
}
