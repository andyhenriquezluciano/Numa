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
import QtQuick
import QtQuick.Controls

// Same compact, neutral control as the mobile lyrics view.
Row {
    id: bar
    property color accentColor: NumaAppearance.textColor
    property color primaryText: NumaAppearance.textColor
    readonly property bool on: NumaTranslation.enabled
    readonly property string status: NumaTranslation.status
    readonly property bool failed: status === "failed"
    readonly property bool loading: on && (status === "downloading" || status === "translating")
    readonly property string label: NumaTranslation.message
        + (status === "downloading" ? " · " + Math.round(NumaTranslation.progress * 100) + "%" : "")
        + (failed ? " · " + qsTr("Try translating again") : "")
    layoutDirection: Qt.RightToLeft

    Button {
        id: toggle
        width: 48
        height: 48
        padding: 0
        hoverEnabled: true
        Accessible.name: bar.on ? qsTr("Stop translating lyrics") : qsTr("Translate lyrics")
        Accessible.description: bar.label
        onClicked: {
            if (bar.on && bar.failed) NumaTranslation.retry();
            else NumaTranslation.enabled = !bar.on;
        }
        background: Item {
            Rectangle {
                anchors.centerIn: parent
                width: 34
                height: 34
                radius: 12
                color: toggle.down ? "#30ffffff" : toggle.hovered ? "#24ffffff"
                    : bar.on ? "#20ffffff" : "#10ffffff"
            }
        }
        contentItem: Item {
            LucideIcon {
                anchors.centerIn: parent
                width: 18
                height: 18
                visible: !bar.loading
                name: "languages"
                color: bar.on ? "#f2ffffff" : "#99ffffff"
            }
            Item {
                anchors.centerIn: parent
                width: 16
                height: 16
                visible: bar.loading
                Canvas {
                    anchors.fill: parent
                    onPaint: {
                        const ctx = getContext("2d");
                        ctx.clearRect(0, 0, width, height);
                        ctx.lineWidth = 1.5;
                        ctx.lineCap = "round";
                        ctx.strokeStyle = "#f2ffffff";
                        ctx.beginPath();
                        ctx.arc(width / 2, height / 2, 6.5, -Math.PI / 2, Math.PI);
                        ctx.stroke();
                    }
                }
                RotationAnimator on rotation {
                    running: bar.loading
                    from: 0
                    to: 360
                    duration: 900
                    loops: Animation.Infinite
                }
            }
        }
        GlassToolTip { control: toggle; text: bar.label || toggle.Accessible.name }
    }
}
