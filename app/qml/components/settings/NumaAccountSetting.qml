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

// Numa account card. Layout lives in NumaAccountSettingForm.ui.qml.
NumaAccountSettingForm {
    id: root

    status: NumaAccount.status
    signedIn: NumaAccount.isSignedIn
    userName: NumaAccount.userName
    userEmail: NumaAccount.userEmail
    userPicture: NumaAccount.userPicture || ""
    initial: (NumaAccount.userName || NumaAccount.userEmail || "?").charAt(0)
    devices: NumaAccount.devices
    devicesLoading: NumaAccount.devicesLoading
    errorMessage: NumaAccount.errorMessage

    function platformLabel(platform) {
        switch (platform) {
        case "linux": return qsTr("Linux");
        case "windows": return qsTr("Windows");
        case "macos": return qsTr("macOS");
        case "android": return qsTr("Android");
        case "ios": return qsTr("iOS");
        default: return qsTr("Unknown");
        }
    }

    onSignedInChanged: if (signedIn) NumaAccount.refreshDevices()
    Component.onCompleted: if (signedIn) NumaAccount.refreshDevices()

    signInButton.onClicked: {
        if (root.signingIn)
            NumaAccount.cancelSignIn();
        NumaAccount.signIn();
    }
    cancelSignInButton.onClicked: NumaAccount.cancelSignIn()
    signOutButton.onClicked: NumaAccount.signOut()
    deviceList.delegate: NumaDeviceRow {
        required property var modelData
        name: modelData.name
        detail: modelData.current
            ? qsTr("%1 · This device").arg(root.platformLabel(modelData.platform))
            : qsTr("%1 · Last seen %2").arg(root.platformLabel(modelData.platform))
                .arg(Qt.formatDate(modelData.lastSeen, Locale.ShortFormat))
        removable: !modelData.current
        removeButton.onClicked: NumaAccount.removeDevice(modelData.id)
    }
}
