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

ColumnLayout {
    id: root
    property bool trayAvailable: true
    property alias persistSwitch: persistSwitch
    property alias windowSwitch: windowSwitch
    property alias traySwitch: traySwitch
    spacing: 28
    Layout.fillWidth: true
    SettingsSection {
        Layout.fillWidth: true
        title: qsTr("Launch")
        rowSpacing: 0
        verticalPadding: 0

        SettingsRow {
            iconName: "history"
            title: qsTr("Restore last session")
            description: qsTr("Pick up the last queue, song, position, and page when NÜMA starts.")

            SettingsSwitch {
                id: persistSwitch
                objectName: "settingsPlaybackPersistence"
                Accessible.name: qsTr("Save queue, current song, and page")
            }
        }

        SettingsRow {
            iconName: "app-window"
            title: qsTr("Remember window size")
            description: qsTr("Reopen NÜMA at the size you last used it.")

            SettingsSwitch {
                id: windowSwitch
                objectName: "settingsRememberWindow"
                Accessible.name: qsTr("Remember window size")
            }
        }

        SettingsRow {
            visible: root.trayAvailable
            iconName: "panel-bottom"
            title: qsTr("Keep playing in the system tray")
            description: qsTr("Closing the window leaves NÜMA running in the tray.")

            SettingsSwitch {
                id: traySwitch
                objectName: "settingsCloseToTray"
                Accessible.name: qsTr("Close to system tray")
            }
        }
    }

}
