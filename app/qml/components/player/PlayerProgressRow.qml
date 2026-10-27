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

// Elapsed time, seek slider and total time. Canopy puts the times under the slider.
Item {
    id: row

    // The player bar this lives in; it owns the colours and helpers.
    required property Item bar
    readonly property bool stacked: bar.canopy

    Layout.fillWidth: true
    Layout.minimumWidth: 160
    Layout.alignment: Qt.AlignVCenter
    implicitHeight: stacked ? 34 : 24

    property real mixAmount: NumaPlayback.crossfadeActive ? 1 : 0
    Behavior on mixAmount { NumberAnimation { duration: 700; easing.type: Easing.OutCubic } }

    // Measure time labels independently of the width assigned by the layout.
    TextMetrics { id: elapsedMetrics; font: elapsed.font; text: elapsed.text }
    TextMetrics { id: totalMetrics; font: total.font; text: total.text }

    Text {
        id: elapsed

        visible: stacked || !bar.compact
        x: 0
        y: stacked ? parent.height - height : (parent.height - height) / 2
        text: bar.timeLabel(progress.pressed ? progress.value : NumaPlayback.displayPosition)
        color: bar.secondaryText
        font.family: "Inter"
        font.pixelSize: stacked ? 9 : 10
        horizontalAlignment: stacked ? Text.AlignLeft : Text.AlignRight
        width: stacked ? elapsedMetrics.width : 30
    }

    Slider {
        id: progress

        x: row.stacked || row.bar.compact ? 0 : elapsed.width + 8
        y: row.stacked ? 0 : (row.height - height) / 2
        width: row.stacked || row.bar.compact ? row.width : Math.max(0, row.width - x - total.width - 8)
        height: row.stacked ? 18 : implicitHeight
        from: 0
        to: Math.max(1, NumaPlayback.displayDuration)
        value: pressed ? value : NumaPlayback.displayPosition
        enabled: NumaPlayback.duration > 0 && !NumaPlayback.loading && !NumaPlayback.errorMessage && !NumaPlayback.crossfadeActive
        live: true
        hoverEnabled: true
        onPressedChanged: {
            if (!pressed && enabled)
                NumaPlayback.seek(value);

        }

        background: Item {
            x: progress.leftPadding
            y: progress.topPadding
            width: progress.availableWidth
            height: progress.availableHeight

            Rectangle {
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width
                height: NumaPlayback.crossfadeActive ? 6 : progress.hovered || progress.pressed ? 6 : 4
                radius: height / 2
                color: "#2effffff"

                Behavior on height {
                    NumberAnimation { duration: 180; easing.type: Easing.OutBack; easing.overshoot: 2 }
                }

                MixDotFill {
                    anchors.fill: parent
                    fraction: progress.visualPosition
                    baseColor: Qt.rgba(1, 1, 1, progress.hovered || progress.pressed ? 1 : 0.82)
                    dotColor: bar.mixAccentColor
                    amount: row.mixAmount
                    maxBand: 160
                    rows: 2
                }
            }
        }

        handle: Item {}

        Behavior on value {
            enabled: NumaPlayback.crossfadeActive
            NumberAnimation {
                duration: 70
                easing.type: Easing.Linear
            }
        }
    }

    Text {
        id: total

        visible: stacked || !bar.compact
        x: parent.width - width
        y: stacked ? parent.height - height : (parent.height - height) / 2
        text: NumaPlayback.displayDuration > 0 ? bar.timeLabel(NumaPlayback.displayDuration) : "-:--"
        color: bar.secondaryText
        font.family: "Inter"
        font.pixelSize: stacked ? 9 : 10
        horizontalAlignment: stacked ? Text.AlignRight : Text.AlignLeft
        width: stacked ? totalMetrics.width : 30
    }
}
