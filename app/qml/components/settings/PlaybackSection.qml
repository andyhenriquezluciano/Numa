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

// Playback settings. Layout lives in PlaybackSectionForm.ui.qml.
PlaybackSectionForm {
    id: root

    streamQuality: NumaPlayback.streamQuality
    crossfadeEnabled: NumaPlayback.crossfadeEnabled
    adaptiveMode: NumaPlayback.adaptiveMix.mode
    audioEngineEnabled: NumaPlayback.audioEngine.enabled
    crossfadeDuration: NumaPlayback.crossfadeDuration
    gaplessEnabled: NumaPlayback.gaplessEnabled
    exponentialVolumeEnabled: NumaPlayback.exponentialVolumeEnabled
    autoplayEnabled: NumaPlayback.autoplayEnabled
    slopAction: NumaPlayback.slop.action
    nonMusicSkipMode: NumaPlayback.nonMusicSkipMode
    queueLayout: NumaPlayback.queueLayout

    streamQualityPicker.onPicked: value => NumaPlayback.streamQuality = value

    Component.onCompleted: {
        if (NumaPlayback.streamQuality === "max")
            NumaPlayback.streamQuality = "high";
    }
    slopPicker.onPicked: value => NumaPlayback.slop.action = value
    nonMusicPicker.onPicked: value => NumaPlayback.nonMusicSkipMode = value
    queueLayoutPicker.onPicked: value => NumaPlayback.queueLayout = value

    crossfadeSwitch.onToggled: {
        // A remembered adaptive mode can't wake up next to a running engine.
        if (crossfadeSwitch.checked && NumaPlayback.audioEngine.enabled && NumaPlayback.adaptiveMix.mode === "adaptive")
            NumaPlayback.adaptiveMix.mode = "standard";
        NumaPlayback.crossfadeEnabled = crossfadeSwitch.checked;
    }
    crossfadeLength.onMoved: function(value) {
        NumaPlayback.crossfadeDuration = Math.round(value);
    }
    gaplessSwitch.onToggled: NumaPlayback.gaplessEnabled = gaplessSwitch.checked
    exponentialVolumeSwitch.onToggled: NumaPlayback.exponentialVolumeEnabled = exponentialVolumeSwitch.checked
    autoplaySwitch.onToggled: NumaPlayback.autoplayEnabled = autoplaySwitch.checked
}
