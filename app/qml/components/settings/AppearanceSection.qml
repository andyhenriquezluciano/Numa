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

// Appearance settings. Layout lives in AppearanceSectionForm.ui.qml.
AppearanceSectionForm {
    showBitrate: NumaAppearance.showBitrate
    fullscreenAutoHide: NumaAppearance.fullscreenAutoHide
    fullscreenPulse: NumaAppearance.fullscreenPulse
    artistVideoBackdrop: NumaAppearance.artistVideoBackdrop
    animatedArtworkEnabled: NumaAppearance.animatedArtworkEnabled
    animatedCollageEnabled: NumaAppearance.animatedCollageEnabled
    theme: NumaAppearance.theme
    bordersEnabled: NumaAppearance.bordersEnabled
    artworkSource: NumaAppearance.artworkSource
    immersiveBackground: NumaAppearance.immersiveBackground
    speed: NumaAppearance.speed
    intensity: NumaAppearance.intensity
    saturation: NumaAppearance.saturation
    brightness: NumaAppearance.brightness
    speedText: NumaAppearance.speed.toFixed(2) + "×"

    bitrateSwitch.onToggled: NumaAppearance.showBitrate = bitrateSwitch.checked
    autoHideSwitch.onToggled: NumaAppearance.fullscreenAutoHide = autoHideSwitch.checked
    pulseSwitch.onToggled: NumaAppearance.fullscreenPulse = pulseSwitch.checked
    artistVideoSwitch.onToggled: NumaAppearance.artistVideoBackdrop = artistVideoSwitch.checked
    animatedArtworkSwitch.onToggled: NumaAppearance.animatedArtworkEnabled = animatedArtworkSwitch.checked
    collageSwitch.onToggled: NumaAppearance.animatedCollageEnabled = collageSwitch.checked
    themePicker.onPicked: value => NumaAppearance.theme = value
    borderPicker.onPicked: value => NumaAppearance.bordersEnabled = value === "default"
    sourcePicker.onPicked: value => NumaAppearance.artworkSource = value
    resetOrderButton.onClicked: NumaAppearance.resetMirrorOrder()
    immersiveSwitch.onToggled: NumaAppearance.immersiveBackground = immersiveSwitch.checked

    speedSlider.onMoved: function(value) { NumaAppearance.speed = value; }
    intensitySlider.onMoved: function(value) { NumaAppearance.intensity = value; }
    saturationSlider.onMoved: function(value) { NumaAppearance.saturation = value; }
    brightnessSlider.onMoved: function(value) { NumaAppearance.brightness = value; }
    resetControlsButton.onClicked: NumaAppearance.reset()
}
