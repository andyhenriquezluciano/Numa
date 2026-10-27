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
import Numa
import "../settings"

WelcomePage {
    title: qsTr("Make it look right")
    subtitle: qsTr("Pick how the player sits and how much the artwork shows off.")

    WelcomeRow {
        title: qsTr("Player layout")
        hint: qsTr("Glade floats at the bottom. Canopy docks at the top beside search.")

        SettingsSegmented {
            model: [
                { value: "glade", label: qsTr("Glade"), name: qsTr("Glade layout") },
                { value: "canopy", label: qsTr("Canopy"), name: qsTr("Canopy layout") }
            ]
            currentValue: NumaAppearance.layoutStyle
            onPicked: value => NumaAppearance.layoutStyle = value
        }
    }

    WelcomeRow {
        title: qsTr("Immersive background")
        hint: qsTr("Let the album art fill the whole window.")

        SettingsSwitch {
            checked: NumaAppearance.immersiveBackground
            onToggled: NumaAppearance.immersiveBackground = checked
            Accessible.name: qsTr("Immersive background")
        }
    }

    WelcomeRow {
        title: qsTr("Animated artwork")
        hint: qsTr("Moving covers for supported albums. Uses more bandwidth.")

        SettingsSwitch {
            checked: NumaAppearance.animatedArtworkEnabled
            onToggled: NumaAppearance.animatedArtworkEnabled = checked
            Accessible.name: qsTr("Animated artwork")
        }
    }

    WelcomeRow {
        title: qsTr("Show audio bitrate")
        hint: qsTr("Display the streaming bitrate in the player bar.")

        SettingsSwitch {
            checked: NumaAppearance.showBitrate
            onToggled: NumaAppearance.showBitrate = checked
            Accessible.name: qsTr("Show audio bitrate")
        }
    }
}
