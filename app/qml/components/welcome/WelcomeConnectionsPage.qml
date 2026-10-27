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
    title: qsTr("Connect your world")
    subtitle: qsTr("Link the services you already use. Accounts are set up in Settings.")

    WelcomeRow {
        title: qsTr("Last.fm")
        hint: qsTr("Scrobble what you listen to.")

        WelcomeButton {
            compact: true
            text: qsTr("Set up")
            onClicked: settingsRequested()
        }
    }

    WelcomeRow {
        title: qsTr("Discord")
        hint: qsTr("Show what you are playing as your status.")

        SettingsSwitch {
            checked: NumaIntegrations.discord.enabled
            onToggled: NumaIntegrations.discord.enabled = checked
            Accessible.name: qsTr("Discord")
        }
    }

    WelcomeRow {
        title: qsTr("Spotify")
        hint: qsTr("Connect your Spotify account.")

        WelcomeButton {
            compact: true
            text: NumaSpotify.connected ? qsTr("Connected") : qsTr("Set up")
            onClicked: settingsRequested()
        }
    }

    WelcomeRow {
        title: qsTr("Qobuz")
        hint: qsTr("Lossless streaming with a Qobuz account.")

        WelcomeButton {
            compact: true
            text: NumaQobuz.connected ? qsTr("Connected") : qsTr("Set up")
            onClicked: settingsRequested()
        }
    }
}
