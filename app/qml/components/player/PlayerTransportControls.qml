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
import QtQuick.Controls
import QtQuick.Layouts

// Previous, play, next and the shuffle and repeat toggles.
RowLayout {
    // The player bar this lives in; it owns the colours and helpers.
    required property Item bar

    Layout.alignment: Qt.AlignVCenter
    spacing: bar.canopy || bar.compact ? 4 : 8

    Repeater {
        model: bar.compact ? ["skip-back", "play", "skip-forward"] : ["shuffle", "skip-back", "play", "skip-forward", "repeat"]

        Button {
            id: transportButton

            required property string modelData
            readonly property bool modeButton: modelData === "shuffle" || modelData === "repeat"
            readonly property bool modeActive: modelData === "shuffle" ? NumaPlayback.shuffleEnabled : modelData === "repeat" && NumaPlayback.repeatMode !== "off"
            readonly property bool mainButton: modelData === "play"
            readonly property string iconName: {
                if (mainButton)
                    return NumaPlayback.playing ? "pause" : "play";

                if (modelData === "repeat" && NumaPlayback.repeatMode === "one")
                    return "repeat-1";
                return modelData;
            }
            readonly property string tooltipText: {
                if (modelData === "shuffle")
                    return NumaPlayback.shuffleEnabled ? qsTr("Shuffle on") : qsTr("Shuffle off");
                if (modelData === "repeat")
                    return NumaPlayback.repeatMode === "one" ? qsTr("Repeat one") : NumaPlayback.repeatMode === "all" ? qsTr("Repeat all") : qsTr("Repeat off");
                if (modelData === "skip-back")
                    return qsTr("Previous");

                if (modelData === "skip-forward")
                    return qsTr("Next");

                return NumaPlayback.playing ? qsTr("Pause") : qsTr("Play");
            }

            enabled: {
                if (modeButton) return true;
                if (mainButton)
                    return !NumaPlayback.loading && Boolean(NumaPlayback.track.id);

                if (modelData === "skip-back")
                    return NumaPlayback.canGoPrevious;

                if (modelData === "skip-forward")
                    return NumaPlayback.canGoNext;

                return false;
            }
            implicitWidth: mainButton ? bar.controlSize + 8 : bar.controlSize
            implicitHeight: implicitWidth
            hoverEnabled: true
            focusPolicy: Qt.TabFocus
            onClicked: {
                switch (modelData) {
                case "shuffle":
                    NumaPlayback.shuffleEnabled = !NumaPlayback.shuffleEnabled;
                    break;
                case "repeat":
                    NumaPlayback.cycleRepeatMode();
                    break;
                case "play":
                    NumaPlayback.toggle();
                    break;
                case "skip-back":
                    NumaPlayback.previous();
                    break;
                case "skip-forward":
                    NumaPlayback.next();
                    break;
                }
            }
            Accessible.name: tooltipText
            Accessible.checkable: modeButton
            Accessible.checked: modeActive

            GlassToolTip {
                control: transportButton
                text: transportButton.tooltipText
                accentColor: bar.accentColor
            }

            background: Rectangle {
                radius: width / 2
                border.width: 1
                border.color: transportButton.activeFocus ? bar.primaryText : "transparent"
                color: {
                    if (transportButton.mainButton) {
                        if (transportButton.down)
                            return "#e5e2da";
                        if (transportButton.hovered)
                            return "#ffffff";
                        return "#f7f5f0";
                    }
                    if (transportButton.modeActive)
                        return transportButton.hovered ? "#3dffffff" : "#2effffff";
                    if (transportButton.hovered && transportButton.enabled)
                        return "#1fffffff";
                    return "transparent";
                }
                opacity: transportButton.enabled ? 1 : 0.4
                scale: transportButton.mainButton && transportButton.down ? 0.94 : 1

                Behavior on color { ColorAnimation { duration: 90 } }
                Behavior on scale { NumberAnimation { duration: 90; easing.type: Easing.OutCubic } }
            }

            contentItem: LucideIcon {
                name: transportButton.iconName
                color: {
                    if (transportButton.mainButton)
                        return "#121212";

                    if (!transportButton.enabled)
                        return "#6b6f68";

                    if (transportButton.modeActive)
                        return "#ffffff";

                    return bar.controlColor;
                }
                implicitWidth: transportButton.mainButton ? 20 : 17
                implicitHeight: implicitWidth
            }
        }
    }
}
