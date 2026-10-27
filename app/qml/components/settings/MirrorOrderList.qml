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

// Reorderable list of motion-artwork mirrors, tried top to bottom.
MirrorOrderListForm {
    mirrors: NumaAppearance.availableMirrors

    mirrorList.delegate: MirrorOrderRow {
        required property var modelData
        required property int index
        position: index
        name: modelData.name || ""
        host: modelData.host || ""
        canMoveUp: index > 0
        canMoveDown: index < NumaAppearance.availableMirrors.length - 1
        upButton.area.onClicked: NumaAppearance.moveMirrorUp(index)
        downButton.area.onClicked: NumaAppearance.moveMirrorDown(index)
    }
}
