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

// Audio engine settings. Layout lives in AudioEngineSectionForm.ui.qml.
AudioEngineSectionForm {
    id: root

    engine: NumaPlayback.audioEngine
    adaptiveMixOn: NumaPlayback.crossfadeEnabled && NumaPlayback.adaptiveMix.mode === "adaptive"
    trackLoaded: NumaPlayback.track.id !== undefined
    bandTexts: bands.map((band) => Number(engine.gains[band.index] || 0).toFixed(1) + " dB")
    preampDb: engine.preampDb
    q: engine.q
    balance: engine.balance
    outputGainDb: engine.outputGainDb
    trackGainDb: engine.trackGainDb
    preampText: Number(engine.preampDb).toFixed(1) + " dB"
    qText: "Q " + Number(engine.q).toFixed(1)
    balanceText: Math.abs(engine.balance) < 0.01 ? qsTr("Center")
        : engine.balance < 0 ? qsTr("Left %1%").arg(Math.round(-engine.balance * 100))
        : qsTr("Right %1%").arg(Math.round(engine.balance * 100))
    outputGainText: Number(engine.outputGainDb).toFixed(1) + " dB"
    trackGainText: Number(engine.trackGainDb).toFixed(1) + " dB"

    engineSwitch.onToggled: engine.enabled = engineSwitch.checked
    autoEqSwitch.onToggled: engine.autoEqEnabled = autoEqSwitch.checked
    eqSwitch.onToggled: engine.eqEnabled = eqSwitch.checked
    levelingSwitch.onToggled: engine.normalizationEnabled = levelingSwitch.checked
    resetButton.onClicked: engine.resetEngine()

    preampSlider.onMoved: value => engine.preampDb = value
    qSlider.onMoved: value => engine.q = value
    balanceSlider.onMoved: value => engine.balance = value
    gainSlider.onMoved: value => engine.outputGainDb = value
    trackGainSlider.onMoved: value => engine.trackGainDb = value

    outputDevice.currentIndex: {
        const devices = engine.outputDevices || [];
        const selectedId = engine.outputDeviceId || "default";
        for (let index = 0; index < devices.length; ++index) {
            if (String(devices[index].id) === selectedId)
                return index;
        }
        return devices.length > 0 ? 0 : -1;
    }
    outputDevice.onActivated: engine.outputDeviceId = outputDevice.currentValue

    presetPicker.onPicked: value => root.engine.applyPreset(value)

    bandList.delegate: AppearanceSlider {
        required property var modelData
        Layout.fillWidth: true
        label: modelData.label
        displayValue: root.bandTexts[modelData.index] || ""
        value: Number(root.engine.gains[modelData.index] || 0)
        from: -12
        to: 12
        stepSize: 0.5
        onMoved: value => root.engine.setBandGain(modelData.index, value)
    }
}
