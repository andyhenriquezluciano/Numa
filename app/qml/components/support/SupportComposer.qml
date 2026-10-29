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

pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import ".."
import "../docs"
import "../settings"

// New report form. Every field writes straight into NumaSupport's draft,
// so closing the popup to capture a screen loses nothing.
Item {
    id: root

    signal closeRequested
    signal captureRequested

    property bool showDiagnostics: false

    function placeholder(kind) {
        if (kind === "feature")
            return qsTr("What would you like NÜMA to do, and what would you use it for?");
        if (kind === "feedback")
            return qsTr("Tell us what you think.");
        return qsTr("What happened, and what did you expect? Steps that make it happen again help the most.");
    }

    function syncFields() {
        if (titleField.text !== NumaSupport.draftTitle)
            titleField.text = NumaSupport.draftTitle;
        if (bodyField.text !== NumaSupport.draftBody)
            bodyField.text = NumaSupport.draftBody;
    }

    Component.onCompleted: {
        syncFields();
        titleField.forceActiveFocus();
    }

    Connections {
        target: NumaSupport
        function onDraftChanged() {
            root.syncFields();
        }
    }

    FileDialog {
        id: imageDialog
        title: qsTr("Choose a screenshot")
        nameFilters: [qsTr("Images (*.png *.jpg *.jpeg *.webp *.bmp *.gif)")]
        onAccepted: NumaSupport.attachFile(selectedFile)
    }

    ScrollView {
        id: scroll
        anchors.fill: parent
        contentWidth: availableWidth
        clip: true

        ColumnLayout {
            width: scroll.availableWidth
            spacing: 14

            RowLayout {
                Layout.fillWidth: true
                Layout.topMargin: 22
                Layout.leftMargin: 28
                Layout.rightMargin: 18
                spacing: 12

                Text {
                    Layout.fillWidth: true
                    text: qsTr("New report")
                    color: NumaAppearance.textColor
                    font.family: "Inter"
                    font.pixelSize: 22
                    font.weight: Font.DemiBold
                }

                DocsPillButton {
                    iconName: "x"
                    text: qsTr("Close")
                    onClicked: root.closeRequested()
                }
            }

            ColumnLayout {
                Layout.fillWidth: true
                Layout.leftMargin: 28
                Layout.rightMargin: 28
                spacing: 14

                SettingsSegmented {
                    model: [
                        { value: "bug", label: qsTr("Bug") },
                        { value: "feature", label: qsTr("Idea") },
                        { value: "feedback", label: qsTr("Feedback") }
                    ]
                    currentValue: NumaSupport.draftKind
                    onPicked: value => NumaSupport.draftKind = value
                }

                TextField {
                    id: titleField
                    Layout.fillWidth: true
                    implicitHeight: 40
                    leftPadding: 14
                    rightPadding: 14
                    selectByMouse: true
                    maximumLength: 140
                    placeholderText: qsTr("Short title, like \"Lyrics stop scrolling after a skip\"")
                    placeholderTextColor: "#727970"
                    color: NumaAppearance.textColor
                    font.family: "Inter"
                    font.pixelSize: 13
                    onTextEdited: NumaSupport.draftTitle = text
                    background: Rectangle {
                        radius: 10
                        color: "#1b201c"
                        border.color: titleField.activeFocus ? "#c4a7e7" : "#3a413b"
                    }
                }

                ScrollView {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 150
                    background: Rectangle {
                        radius: 10
                        color: "#1b201c"
                        border.color: bodyField.activeFocus ? "#c4a7e7" : "#3a413b"
                    }

                    TextArea {
                        id: bodyField
                        leftPadding: 14
                        rightPadding: 14
                        topPadding: 12
                        bottomPadding: 12
                        wrapMode: TextEdit.Wrap
                        selectByMouse: true
                        placeholderText: root.placeholder(NumaSupport.draftKind)
                        placeholderTextColor: "#727970"
                        color: NumaAppearance.textColor
                        font.family: "Inter"
                        font.pixelSize: 13
                        background: null
                        onTextChanged: {
                            if (text !== NumaSupport.draftBody)
                                NumaSupport.draftBody = text;
                        }
                    }
                }

                SupportScreenshot {
                    Layout.fillWidth: true
                    onCaptureRequested: root.captureRequested()
                    onChooseRequested: imageDialog.open()
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 12

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2
                        Text {
                            text: qsTr("Include system details")
                            color: NumaAppearance.textColor
                            font.family: "Inter"
                            font.pixelSize: 13
                        }
                        Text {
                            Layout.fillWidth: true
                            wrapMode: Text.WordWrap
                            text: qsTr("NÜMA version, operating system, graphics API and window size. Nothing about your music or accounts.")
                            color: "#a4aaa1"
                            font.family: "Inter"
                            font.pixelSize: 11
                        }
                    }

                    DocsPillButton {
                        visible: NumaSupport.draftDiagnostics
                        iconName: root.showDiagnostics ? "chevron-up" : "chevron-down"
                        text: root.showDiagnostics ? qsTr("Hide") : qsTr("Show")
                        onClicked: root.showDiagnostics = !root.showDiagnostics
                    }

                    SettingsSwitch {
                        checked: NumaSupport.draftDiagnostics
                        Accessible.name: qsTr("Include system details")
                        onToggled: NumaSupport.draftDiagnostics = checked
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    visible: root.showDiagnostics && NumaSupport.draftDiagnostics
                    implicitHeight: diagnosticsText.implicitHeight + 24
                    radius: 10
                    color: "#141815"
                    border.color: "#2a302b"

                    Text {
                        id: diagnosticsText
                        anchors.fill: parent
                        anchors.margins: 12
                        text: NumaSupport.diagnostics
                        wrapMode: Text.WrapAnywhere
                        color: "#b8bab3"
                        font.family: "monospace"
                        font.pixelSize: 11
                    }
                }

                Text {
                    Layout.fillWidth: true
                    visible: NumaSupport.errorMessage.length > 0
                    wrapMode: Text.WordWrap
                    text: NumaSupport.errorMessage
                    color: "#e8a598"
                    font.family: "Inter"
                    font.pixelSize: 12
                }

                RowLayout {
                    Layout.fillWidth: true
                    Layout.bottomMargin: 24
                    spacing: 12

                    Text {
                        Layout.fillWidth: true
                        wrapMode: Text.WordWrap
                        text: (NumaSupport.screenshotUrl
                               ? qsTr("Posted publicly as an issue on %1 by @%2, screenshot included.")
                               : qsTr("Posted publicly as an issue on %1 by @%2."))
                            .arg(NumaSupport.repository || "GitHub").arg(NumaSupport.githubLogin)
                        color: "#7c857f"
                        font.family: "Inter"
                        font.pixelSize: 11
                    }

                    SettingsButton {
                        text: qsTr("Discard")
                        enabled: !NumaSupport.submitting
                        onClicked: NumaSupport.discardDraft()
                    }

                    SettingsButton {
                        highlighted: true
                        enabled: !NumaSupport.submitting
                        text: NumaSupport.submitting ? qsTr("Sending...") : qsTr("Send report")
                        onClicked: NumaSupport.submit()
                    }
                }
            }
        }

        FlickableWheel {
            view: scroll.contentItem
        }
    }
}
