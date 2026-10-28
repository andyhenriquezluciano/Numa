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
import QtQuick.Controls
import QtQuick.Layouts

// Lyric translation source, credentials, and downloaded local models.
SettingsSection {
    id: root

    property var providerValues: ["google", "local"]
    property bool translationEnabled: true
    property string quality: "standard"
    property string provider: "google"
    property string targetLanguage: "spanish"
    // Downloaded packs: { name, code, quality, sizeBytes, credit }.
    property var installed: []
    property alias enableSwitch: enableSwitch
    property alias qualityPicker: qualityPicker
    property alias providerPicker: providerPicker
    property alias targetPicker: targetPicker
    property alias packList: packList

    title: qsTr("Lyric translation")
    rowSpacing: 0
    verticalPadding: 0

    SettingsRow {
        iconName: "languages"
        title: root.targetLanguage === "spanish" ? qsTr("Translate lyrics to Spanish") : qsTr("Translate lyrics to English")
        description: root.provider === "google"
            ? qsTr("Google Translate, with local translation when the web service is unavailable.")
            : qsTr("NÜMA translates supported languages on this computer. Lyrics never leave your computer.")

        SettingsSwitch {
            id: enableSwitch
            checked: root.translationEnabled
            Accessible.name: root.targetLanguage === "spanish" ? qsTr("Translate lyrics to Spanish") : qsTr("Translate lyrics to English")
        }
    }

    SettingsRow {
        iconName: "languages"
        title: qsTr("Translation language")
        description: qsTr("Choose the language for translated lyrics.")

        SettingsSegmented {
            id: targetPicker
            currentValue: root.targetLanguage
            model: [
                { value: "spanish", label: qsTr("Spanish"), name: qsTr("Translate lyrics to Spanish") },
                { value: "english", label: qsTr("English"), name: qsTr("Translate lyrics to English") }
            ]
        }
    }

    SettingsRow {
        iconName: "plug"
        title: qsTr("Translation source")
        description: qsTr("Choose online or local translation.")
        helpText: qsTr("Google Translate requires an internet connection. If Google blocks the web request, NÜMA uses a local model and identifies it in the translation status. Local models download once and then work offline; supported languages depend on the model.")

        SettingsSelect {
            id: providerPicker
            Layout.preferredWidth: 185
            model: [qsTr("Google Translate"), qsTr("Local")]
            currentIndex: root.providerValues.indexOf(root.provider)
            displayText: currentIndex < 0 ? qsTr("Choose source") : currentText
            Accessible.name: qsTr("Translation source")
        }
    }

    SettingsRow {
        visible: root.provider === "local"
        iconName: "sparkles"
        title: qsTr("Translation quality")
        description: root.quality === "high" ? qsTr("Better translations with larger models.") : qsTr("Smaller models with faster translations.")
        helpText: qsTr("High quality uses about 85 MB per language and more memory. Standard uses about 20 MB per language. Speed depends on your computer and the length of the lyrics.")

        SettingsSegmented {
            id: qualityPicker
            currentValue: root.quality
            model: [
                { value: "standard", label: qsTr("Standard"), name: qsTr("Standard translation quality") },
                { value: "high", label: qsTr("High"), name: qsTr("High translation quality") }
            ]
        }
    }

    SettingsRow {
        visible: root.provider === "local" && root.installed.length === 0
        iconName: "hard-drive"
        title: qsTr("No language models downloaded")
        description: qsTr("Models download when a song needs one.")
    }

    // The logic layer swaps in a delegate that removes the pack.
    Repeater {
        id: packList
        model: root.provider === "local" ? root.installed : []

        SettingsRow {
            required property var modelData
            iconName: "hard-drive"
            title: modelData.quality === "high" ? qsTr("%1 model (High)").arg(modelData.name)
                                                : qsTr("%1 model").arg(modelData.name)
            description: qsTr("%1 on this computer. %2.").arg(qsTr("%1 MB").arg(Math.round(modelData.sizeBytes / 1e6))).arg(modelData.credit)

            SettingsButton {
                text: qsTr("Remove")
            }
        }
    }
}
