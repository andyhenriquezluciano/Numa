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
    title: qsTr("Tune the sound")
    subtitle: qsTr("Choose how songs stream and how they flow into each other.")

    WelcomeRow {
        title: qsTr("Stream quality")
        hint: qsTr("Higher quality uses more data.")

        SettingsSegmented {
            model: [
                { value: "saver", label: qsTr("Saver"), name: qsTr("Saver quality") },
                { value: "normal", label: qsTr("Normal"), name: qsTr("Normal quality") },
                { value: "high", label: qsTr("High"), name: qsTr("High quality") }
            ]
            currentValue: NumaPlayback.streamQuality
            onPicked: value => NumaPlayback.streamQuality = value
        }
    }

    WelcomeRow {
        title: qsTr("Exponential volume")
        hint: qsTr("Make the volume slider more precise at low volumes.")

        SettingsSwitch {
            checked: NumaPlayback.exponentialVolumeEnabled
            onToggled: NumaPlayback.exponentialVolumeEnabled = checked
            Accessible.name: qsTr("Exponential volume")
        }
    }

    WelcomeRow {
        title: qsTr("Crossfade")
        hint: qsTr("Blend the end of a song into the next one.")

        SettingsSwitch {
            checked: NumaPlayback.crossfadeEnabled
            onToggled: {
                // Adaptive mixing cannot run next to the audio engine.
                if (checked && NumaPlayback.audioEngine.enabled && NumaPlayback.adaptiveMix.mode === "adaptive")
                    NumaPlayback.adaptiveMix.mode = "standard";
                NumaPlayback.crossfadeEnabled = checked;
            }
            Accessible.name: qsTr("Crossfade")
        }
    }

    WelcomeRow {
        title: qsTr("Gapless playback")
        hint: qsTr("Remove the silence between tracks on albums.")

        SettingsSwitch {
            checked: NumaPlayback.gaplessEnabled
            onToggled: NumaPlayback.gaplessEnabled = checked
            Accessible.name: qsTr("Gapless playback")
        }
    }

    WelcomeRow {
        title: qsTr("Autoplay")
        hint: qsTr("Keep similar songs coming when the queue ends.")

        SettingsSwitch {
            checked: NumaPlayback.autoplayEnabled
            onToggled: NumaPlayback.autoplayEnabled = checked
            Accessible.name: qsTr("Autoplay")
        }
    }

    WelcomeRow {
        title: qsTr("Save plays to YouTube Music")
        hint: qsTr("Songs you play are added to your YouTube Music history.")

        SettingsSwitch {
            checked: NumaPlayback.youtubeHistoryEnabled
            onToggled: NumaPlayback.youtubeHistoryEnabled = checked
            Accessible.name: qsTr("Save plays to YouTube Music")
        }
    }
}
