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

// Crossfade style row. Layout lives in CrossfadeModeForm.ui.qml.
CrossfadeModeForm {
    crossfadeEnabled: NumaPlayback.crossfadeEnabled
    adaptiveAvailable: NumaPlayback.adaptiveMix.available
    audioEngineEnabled: NumaPlayback.audioEngine.enabled
    adaptiveMode: NumaPlayback.adaptiveMix.mode
    adaptiveStatus: NumaPlayback.adaptiveMix.status

    picker.onPicked: value => {
        if (value === "adaptive" && NumaPlayback.audioEngine.enabled)
            NumaPlayback.audioEngine.enabled = false;
        NumaPlayback.adaptiveMix.mode = value;
    }
}
