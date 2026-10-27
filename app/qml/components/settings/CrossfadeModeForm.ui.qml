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

import QtQuick

// Standard or adaptive crossfade; lives inside the Transitions group.
SettingsRow {
    id: root

    property bool crossfadeEnabled: true
    property bool adaptiveAvailable: true
    property bool audioEngineEnabled: false
    // "standard" or "adaptive".
    property string adaptiveMode: "standard"
    property string adaptiveStatus: ""
    property alias picker: picker

    iconName: "shuffle"
    title: qsTr("Crossfade style")
    enabled: crossfadeEnabled
    description: !adaptiveAvailable ? qsTr("Adaptive mix is unavailable at MAX quality.")
                 : adaptiveMode === "adaptive" ? qsTr("Adjust transitions to each pair of songs.") : qsTr("Use a fixed transition duration.")
    helpText: qsTr("Standard uses the crossfade duration you choose. Adaptive mix analyzes beats and phrases on this computer to shape the transition. It is unavailable at MAX streaming quality and turns off the Audio Engine.")

    SettingsSegmented {
        id: picker
        currentValue: root.adaptiveMode
        model: [
            { value: "standard", label: qsTr("Standard") },
            {
                value: "adaptive", label: qsTr("Adaptive mix"),
                enabled: root.adaptiveAvailable
            }
        ]
    }
}
