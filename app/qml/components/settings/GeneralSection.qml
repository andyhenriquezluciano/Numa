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
import QtQuick.Layouts

// Launch behavior.
GeneralSectionForm {
    id: root

    trayAvailable: NumaTray.available

    persistSwitch.checked: NumaPlayback.playbackPersistenceEnabled
    persistSwitch.onToggled: NumaPlayback.playbackPersistenceEnabled = persistSwitch.checked

    windowSwitch.checked: NumaWindowState.enabled
    windowSwitch.onToggled: NumaWindowState.enabled = windowSwitch.checked

    traySwitch.checked: NumaTray.closeToTray
    traySwitch.onToggled: NumaTray.closeToTray = traySwitch.checked

    SettingsSection {
        Layout.fillWidth: true
        title: qsTr("Updates")
        SettingsRow {
            iconName: "refresh-cw"
            title: qsTr("Check for updates")
            description: NumaUpdates.state === "checking" ? qsTr("Checking for updates…")
                : NumaUpdates.state === "error" ? NumaUpdates.error
                : NumaUpdates.state === "up-to-date" ? qsTr("You have the latest version.")
                : NumaUpdates.state === "available" ? qsTr("Version %1 is available.").arg(NumaUpdates.latestVersion)
                : NumaUpdates.state === "ready" ? qsTr("Update ready. Restart Numa to install.")
                : qsTr("Installed version: %1").arg(NumaUpdates.currentVersion)
            SettingsButton {
                text: qsTr("Check for updates")
                buttonColor: NumaAppearance.theme === "rose_pine" ? "#ebbcba"
                    : NumaAppearance.theme === "rose_pine_dawn" ? NumaAppearance.highlightColor : (highlighted ? "#514060" : down ? "#2cffffff" : hovered ? "#1cffffff" : "#0fffffff")
                labelColor: NumaAppearance.theme === "rose_pine" ? NumaAppearance.baseColor : NumaAppearance.textColor
                enabled: NumaUpdates.available && ["checking", "downloading", "staging"].indexOf(NumaUpdates.state) < 0
                onClicked: NumaUpdates.checkForUpdates()
            }
        }
        SettingsRow {
            visible: NumaUpdates.state === "available" || NumaUpdates.state === "ready"
            title: qsTr("Update NÜMA")
            description: qsTr("Download the Windows installer, then run it to update NÜMA.")
            SettingsButton {
                prominent: true
                text: NumaUpdates.state === "ready" ? qsTr("Restart to update") : qsTr("Download installer")
                onClicked: NumaUpdates.state === "ready" ? NumaUpdates.restartToUpdate() : NumaUpdates.downloadInstaller()
            }
        }
    }

}
