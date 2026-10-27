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
import ".."
import "../settings"

// What reports need before the first one: an Numa account, then GitHub.
Item {
    id: root

    signal closeRequested

    readonly property bool signedIn: NumaAccount.isSignedIn
    readonly property bool signingIn: NumaAccount.status === "signing_in"

    ColumnLayout {
        anchors.centerIn: parent
        width: Math.min(440, parent.width - 64)
        spacing: 14

        LucideIcon {
            Layout.alignment: Qt.AlignHCenter
            Layout.preferredWidth: 34
            Layout.preferredHeight: 34
            name: root.signedIn ? "github" : "bug"
            color: "#c4e0cb"
        }

        Text {
            Layout.fillWidth: true
            horizontalAlignment: Text.AlignHCenter
            text: root.signedIn ? qsTr("Link your GitHub account") : qsTr("Sign in to report a bug")
            color: NumaAppearance.textColor
            font.family: "Inter"
            font.pixelSize: 19
            font.weight: Font.DemiBold
        }

        Text {
            Layout.fillWidth: true
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
            text: root.signedIn
                ? qsTr("Reports become public GitHub issues under your name, so maintainers can ask you questions. NÜMA only reads your GitHub username and avatar.")
                : qsTr("Reports go through your NÜMA account, so NÜMA can tell you when someone replies, a fix lands, or the report closes.")
            color: "#b8bab3"
            font.family: "Inter"
            font.pixelSize: 13
            lineHeight: 1.25
        }

        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 6
            spacing: 10

            SettingsButton {
                visible: !root.signedIn
                highlighted: !root.signingIn
                text: root.signingIn ? qsTr("Open browser again") : qsTr("Sign in with Google")
                onClicked: {
                    if (root.signingIn)
                        NumaAccount.cancelSignIn();
                    NumaAccount.signIn();
                }
            }

            SettingsButton {
                visible: root.signedIn
                highlighted: !NumaSupport.githubLinking
                text: NumaSupport.githubLinking ? qsTr("Open GitHub again") : qsTr("Link GitHub")
                onClicked: NumaSupport.linkGithub()
            }

            SettingsButton {
                visible: root.signingIn || NumaSupport.githubLinking
                text: qsTr("Cancel")
                onClicked: root.signingIn ? NumaAccount.cancelSignIn() : NumaSupport.cancelGithubLink()
            }

            SettingsButton {
                visible: !root.signingIn && !NumaSupport.githubLinking
                text: qsTr("Close")
                onClicked: root.closeRequested()
            }
        }

        Text {
            Layout.fillWidth: true
            horizontalAlignment: Text.AlignHCenter
            visible: NumaSupport.githubLinking || root.signingIn
            text: qsTr("Finish in your browser. NÜMA picks it up on its own.")
            color: "#a4aaa1"
            font.family: "Inter"
            font.pixelSize: 12
        }

        Text {
            Layout.fillWidth: true
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
            visible: text.length > 0
            text: NumaSupport.errorMessage || NumaAccount.errorMessage
            color: "#e8a598"
            font.family: "Inter"
            font.pixelSize: 12
        }
    }
}
