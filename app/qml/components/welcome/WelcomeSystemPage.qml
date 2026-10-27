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
    title: qsTr("Fit it to your desktop")
    subtitle: qsTr("Control how NÜMA starts, closes and updates.")

    WelcomeRow {
        title: qsTr("Restore last session")
        hint: qsTr("Reopen with your last queue, song and page.")

        SettingsSwitch {
            checked: NumaPlayback.playbackPersistenceEnabled
            onToggled: NumaPlayback.playbackPersistenceEnabled = checked
            Accessible.name: qsTr("Restore last session")
        }
    }

    WelcomeRow {
        title: qsTr("Remember window size")
        hint: qsTr("Reopen NÜMA at the size you last used.")

        SettingsSwitch {
            checked: NumaWindowState.enabled
            onToggled: NumaWindowState.enabled = checked
            Accessible.name: qsTr("Remember window size")
        }
    }

    WelcomeRow {
        visible: NumaTray.available
        title: qsTr("Keep playing in the tray")
        hint: qsTr("Closing the window leaves NÜMA running in the tray.")

        SettingsSwitch {
            checked: NumaTray.closeToTray
            onToggled: NumaTray.closeToTray = checked
            Accessible.name: qsTr("Close to system tray")
        }
    }

    WelcomeRow {
        title: qsTr("Download updates automatically")
        hint: qsTr("New versions install the next time NÜMA starts.")

        SettingsSwitch {
            checked: NumaUpdates.autoUpdate
            onToggled: NumaUpdates.setAutoUpdate(checked)
            Accessible.name: qsTr("Download updates automatically")
        }
    }
}
