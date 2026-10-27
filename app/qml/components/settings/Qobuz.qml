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

// Qobuz connection card. Layout lives in QobuzForm.ui.qml.
QobuzForm {
    status: NumaQobuz.status
    active: NumaQobuz.active
    connected: NumaQobuz.connected
    message: NumaQobuz.message
    messageIsError: NumaQobuz.messageIsError

    connectButton.onClicked: NumaQobuz.connectAccount()
    cancelButton.onClicked: NumaQobuz.cancelConnection()
    disconnectButton.onClicked: NumaQobuz.disconnectAccount()
}
