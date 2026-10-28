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
import QtQuick.Layouts
import ".."

// Sidebar, header and scrolling page of the settings popup.
RowLayout {
    id: root

    // Nav entries; key doubles as the section id used by the loader switch.
    readonly property var sections: [
        { key: "general", label: qsTr("General"), icon: "sliders-horizontal", blurb: qsTr("Startup and window behavior.") },
        { key: "accounts", label: qsTr("Accounts"), icon: "circle-user-round", blurb: qsTr("Your accounts, library, and synchronization.") },
        { key: "lyrics", label: qsTr("Lyrics and translation"), icon: "languages", blurb: qsTr("Translation languages and offline models.") },
        { key: "playback", label: qsTr("Playback"), icon: "audio-lines", blurb: qsTr("Quality, audio engine, and transitions between songs.") },
        { key: "downloads", label: qsTr("Downloads"), icon: "download", blurb: qsTr("Offline listening and storage.") },
        { key: "appearance", label: qsTr("Appearance"), icon: "palette", blurb: qsTr("Artwork, backgrounds, and player bar details.") },
        { key: "integrations", label: qsTr("Integrations"), icon: "plug", blurb: qsTr("Connect NÜMA to other apps.") },
        { key: "shortcuts", label: qsTr("Shortcuts"), icon: "keyboard", blurb: qsTr("Keyboard shortcuts for playback and navigation.") }
    ]
    property int currentIndex: 0
    readonly property var currentSection: sections[currentIndex]
    readonly property string activeSection: currentSection.key
    property alias navList: navList
    property alias closeButton: closeButton
    property alias headerFade: headerFade
    property alias scroller: scroller
    property alias sectionLoader: sectionLoader

    spacing: 0
    clip: true

    ColumnLayout {
        Layout.preferredWidth: 216
        Layout.fillHeight: true
        Layout.margins: 20
        Layout.rightMargin: 16
        spacing: 2

        Text {
            text: qsTr("Settings")
            color: NumaAppearance.mutedColor
            font.family: "Inter"
            font.pixelSize: 12
            font.weight: Font.DemiBold
            font.letterSpacing: 0.6
            font.capitalization: Font.AllUppercase
            Layout.leftMargin: 12
            Layout.topMargin: 6
            Layout.bottomMargin: 12
        }

        Label {
            text: qsTr("Language")
            color: NumaAppearance.mutedColor
            font.pixelSize: 12
            Layout.leftMargin: 12
        }
        SettingsSelect {
            Layout.fillWidth: true
            Layout.bottomMargin: 18
            model: ["English", "Español"]
            currentIndex: NumaLanguage.language === "en" ? 0 : 1
            onActivated: index => NumaLanguage.language = ["en", "es"][index]
            Accessible.name: qsTr("Language")
        }
        Item {
            Layout.fillWidth: true
            implicitHeight: navColumn.implicitHeight

            // One pill that glides between entries; the nav's own little curling stone.
            Rectangle {
                width: parent.width
                height: 40
                radius: 10
                color: NumaAppearance.rosePine ? "#26233a" : "transparent"
                // Translucent so the glass shows through the selection.
                gradient: NumaAppearance.rosePine ? null : selectionGradient
                Gradient {
                    id: selectionGradient
                    GradientStop { position: 0.0; color: NumaAppearance.rosePine ? "#33ebbcba" : "#338cc4a0" }
                    GradientStop { position: 1.0; color: NumaAppearance.rosePine ? "#1aebbcba" : "#1a8cc4a0" }
                }
                border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : "#38a0d6b4"
                border.width: NumaAppearance.controlBorderWidth
                y: root.currentIndex * (height + navColumn.spacing)
                Behavior on y { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }
                Rectangle {
                    visible: NumaAppearance.rosePine
                    x: 0; y: 11; width: 3; height: 18; radius: 1.5
                    color: NumaAppearance.accentColor
                }
            }

            Column {
                id: navColumn
                width: parent.width
                spacing: 2

                // The logic layer swaps in a delegate that selects the section on click.
                Repeater {
                    id: navList
                    model: root.sections

                    delegate: SettingsNavItem {
                        required property var modelData
                        required property int index
                        width: navColumn.width
                        entry: modelData
                        current: root.currentIndex === index
                    }
                }
            }
        }

        Item {
            Layout.fillHeight: true
        }

    }

    Rectangle {
        Layout.fillHeight: true
        Layout.preferredWidth: 1
        color: "#1cffffff"
    }

    ColumnLayout {
        Layout.fillWidth: true
        Layout.fillHeight: true
        spacing: 0

        RowLayout {
            Layout.fillWidth: true
            Layout.topMargin: 26
            Layout.leftMargin: 36
            Layout.rightMargin: 20
            Layout.bottomMargin: 18
            spacing: 16

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 6

                NumberAnimation on opacity {
                    id: headerFade
                    from: 0
                    to: 1
                    duration: 220
                    easing.type: Easing.OutCubic
                }

                Text {
                    Layout.fillWidth: true
                    text: root.currentSection.label
                    color: NumaAppearance.textColor
                    font.family: "Inter"
                    font.pixelSize: 26
                    font.weight: Font.DemiBold
                }

                Text {
                    Layout.fillWidth: true
                    text: root.currentSection.blurb
                    color: NumaAppearance.mutedColor
                    font.family: "Inter"
                    font.pixelSize: 13
                    wrapMode: Text.WordWrap
                }
            }

            RoundButton {
                id: closeButton
                Layout.alignment: Qt.AlignTop
                implicitWidth: 34
                implicitHeight: 34
                hoverEnabled: true
                Accessible.name: qsTr("Close settings")

                contentItem: Item {
                    LucideIcon {
                        anchors.centerIn: parent
                        width: 16
                        height: 16
                        name: "x"
                        color: NumaAppearance.textColor
                    }
                }

                background: Rectangle {
                    radius: width / 2
                    color: closeButton.down ? "#30ffffff" : closeButton.hovered ? "#20ffffff" : "#10ffffff"
                    border.color: closeButton.visualFocus ? "#a6d4bf" : "transparent"
                }
            }
        }

        ScrollView {
            id: scroller
            Layout.fillWidth: true
            Layout.fillHeight: true
            contentWidth: availableWidth
            clip: true
            Layout.bottomMargin: 18

            Flickable {
                id: sectionViewport
                width: scroller.availableWidth
                height: scroller.availableHeight
                contentWidth: width
                contentHeight: sectionLoader.implicitHeight + 36
                boundsBehavior: Flickable.StopAtBounds
                boundsMovement: Flickable.StopAtBounds
                clip: true

                Loader {
                    id: sectionLoader
                    x: 36
                    y: 12
                    // Right inset keeps the scrollbar clear of section cards.
                    width: parent.width - 36 - 28
                    sourceComponent: root.activeSection === "integrations"
                                     ? integrationsComponent
                                     : root.activeSection === "playback" ? playbackComponent
                                     : root.activeSection === "downloads" ? downloadsComponent
                                     : root.activeSection === "appearance" ? appearanceComponent
                                     : root.activeSection === "shortcuts" ? shortcutsComponent
                                     : root.activeSection === "accounts" ? accountsComponent
                                     : root.activeSection === "lyrics" ? lyricsComponent
                                     : generalComponent
                }
            }

            FlickableWheel {
                view: sectionViewport
                overshootEnabled: false
            }
        }
    }

    Component { id: accountsComponent; AccountsSection {} }
    Component { id: lyricsComponent; LyricTranslationSettings {} }
    Component { id: generalComponent; GeneralSection {} }

    Component {
        id: downloadsComponent
        DownloadsSection {}
    }

    Component {
        id: appearanceComponent
        AppearanceSection {}
    }

    Component {
        id: integrationsComponent
        IntegrationsSection {}
    }

    Component {
        id: playbackComponent
        PlaybackSection {}
    }

    Component {
        id: shortcutsComponent
        ShortcutsSection {}
    }

}
