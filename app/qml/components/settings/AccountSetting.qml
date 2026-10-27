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

// YouTube Music account card. Layout lives in AccountSettingForm.ui.qml.
AccountSettingForm {
    id: root

    signal signOutRequested

    userName: NumaAuth.userName
    userHandle: NumaAuth.userHandle || ""
    userAvatar: NumaAuth.userAvatar || ""
    initial: (NumaAuth.userName || "?").charAt(0)
    signedIn: NumaAuth.isSignedIn
    switching: NumaAuth.accountSwitching

    switchButton.onClicked: NumaAuth.switchAccount()
    signOutButton.onClicked: root.signOutRequested()
}
