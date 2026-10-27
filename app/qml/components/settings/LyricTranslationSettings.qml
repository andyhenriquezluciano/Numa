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

// Lyric translation settings. Layout lives in LyricTranslationSettingsForm.ui.qml.
LyricTranslationSettingsForm {
    id: root

    translationEnabled: NumaTranslation.enabled
    quality: NumaTranslation.quality
    provider: NumaTranslation.provider
    targetLanguage: NumaTranslation.targetLanguage
    installed: installedPacks()

    // Untyped so qmlcachegen leaves the QVariantList alone.
    function installedPacks() {
        const packs = NumaTranslation.packs;
        return packs ? packs.filter(pack => pack.installed) : [];
    }

    enableSwitch.onToggled: NumaTranslation.enabled = enableSwitch.checked
    qualityPicker.onPicked: value => NumaTranslation.quality = value
    providerPicker.onActivated: index => {
        NumaTranslation.provider = root.providerValues[index];
    }
    targetPicker.onPicked: value => NumaTranslation.targetLanguage = value

    packList.delegate: SettingsRow {
        id: packRow
        required property var modelData
        iconName: "hard-drive"
        title: modelData.quality === "high" ? qsTr("%1 model (High)").arg(modelData.name)
                                            : qsTr("%1 model").arg(modelData.name)
        description: qsTr("%1 on this computer. %2.").arg(qsTr("%1 MB").arg(Math.round(modelData.sizeBytes / 1e6))).arg(modelData.credit)

        SettingsButton {
            text: qsTr("Remove")
            Accessible.name: qsTr("Remove the %1").arg(packRow.title)
            onClicked: NumaTranslation.removePack(modelData.code)
        }
    }
}
