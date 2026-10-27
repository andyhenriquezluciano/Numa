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
    signal signOutRequested
    readonly property bool signedIn: NumaAuth.isSignedIn
    readonly property string authError: NumaAuth.errorMessage
    spacing: 28
    SettingsSection {
        Layout.fillWidth: true
        title: qsTr("NÜMA account")
        NumaAccountSetting { Layout.fillWidth: true }
    }
    SettingsSection {
        Layout.fillWidth: true
        title: qsTr("YouTube Music")
        rowSpacing: 0
        verticalPadding: 0

        SettingsRow {
            AccountSetting {
                id: account
                onSignOutRequested: root.signOutRequested()
                Layout.fillWidth: true
            }
        }

        SettingsRow {
            iconName: "clock"
            title: qsTr("Save plays to YouTube Music history")
            description: qsTr("Songs you play in NÜMA are added to your YouTube Music history.")

            SettingsSwitch {
                id: historySwitch
                checked: NumaPlayback.youtubeHistoryEnabled
                onToggled: NumaPlayback.youtubeHistoryEnabled = checked
                objectName: "settingsYouTubeHistory"
                Accessible.name: qsTr("Send listening history to YouTube")
            }
        }

        Text {
            Layout.fillWidth: true
            Layout.topMargin: 4
            Layout.bottomMargin: 14
            visible: root.signedIn && root.authError.length > 0
            text: root.authError
            wrapMode: Text.WordWrap
            color: "#e6a197"
            font.family: "Inter"
            font.pixelSize: 12
        }
    }
}
