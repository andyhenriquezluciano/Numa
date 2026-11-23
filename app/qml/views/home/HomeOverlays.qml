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

import "../../components"
import "../../components/docs"
import "../../components/releases"
import "../../components/settings"
import "../../components/support"
import Numa
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// Settings, docs, release notes, spotlight and support popups plus the shell keyboard shortcuts.
Item {
    id: overlays

    // HomeShell: owns navigation state and the fullscreen player.
    required property Item shell
    // Glass backdrop the popups blur; also hosts the top bar search field.
    required property Item stage

    readonly property bool signedIn: NumaAuth.isSignedIn
    readonly property bool welcomeOpen: false

    anchors.fill: parent
    z: 200

    Connections {
        target: NumaUpdates
        function onChanged() {
            if (NumaUpdates.state === "available" || NumaUpdates.state === "ready")
                updatePrompt.open();
        }
    }

    GlassPopup {
        id: updatePrompt
        objectName: "updatePrompt"
        readonly property bool readyToInstall: NumaUpdates.state === "ready"
        readonly property bool downloading: NumaUpdates.state === "downloading"
        readonly property bool authorizing: NumaUpdates.state === "authorizing"
        readonly property string notes: (NumaUpdates.releaseNotes || "").trim()
        readonly property var noteBlocks: /^[-*+][ \t]/.test(notes)
            ? notes.split(/\r?\n(?:[ \t]*\r?\n)*(?=[-*+][ \t])/)
            : [notes]
        backdrop: overlays.stage
        width: Math.max(1, Math.min(540, parent.width - 32))
        height: Math.max(1, Math.min(implicitHeight, parent.height - 32))
        padding: width < 400 ? 14 : 22

        contentItem: ColumnLayout {
            spacing: 16

            RowLayout {
                Layout.fillWidth: true
                spacing: 12

                Rectangle {
                    Layout.preferredWidth: 42
                    Layout.preferredHeight: 42
                    Layout.alignment: Qt.AlignTop
                    radius: 12
                    color: NumaAppearance.rosePine ? NumaAppearance.highlightColor : "#14ffffff"

                    LucideIcon {
                        anchors.centerIn: parent
                        width: 20
                        height: 20
                        name: updatePrompt.readyToInstall ? "refresh-cw" : "download"
                        color: NumaAppearance.rosePine ? NumaAppearance.textColor : NumaAppearance.accentColor
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 4

                    Text {
                        Layout.fillWidth: true
                        text: qsTr("NÜMA update")
                        color: NumaAppearance.textColor
                        font.family: "Inter"
                        font.pixelSize: 22
                        font.weight: Font.DemiBold
                        wrapMode: Text.Wrap
                    }

                    Text {
                        Layout.fillWidth: true
                        text: updatePrompt.authorizing ? qsTr("Confirm the Windows prompt to continue.")
                            : updatePrompt.downloading ? (NumaUpdates.total > 0
                                ? qsTr("Downloading update… %1%").arg(Math.floor(100 * NumaUpdates.downloaded / NumaUpdates.total))
                                : qsTr("Downloading update…"))
                            : updatePrompt.readyToInstall ? qsTr("Update ready. Restart Numa to install.")
                            : qsTr("Version %1 is available.").arg(NumaUpdates.latestVersion)
                        color: NumaAppearance.mutedColor
                        font.family: "Inter"
                        font.pixelSize: 13
                        wrapMode: Text.Wrap
                    }
                }
            }

            Text {
                objectName: "updateError"
                Layout.fillWidth: true
                visible: text !== ""
                text: NumaUpdates.error
                color: NumaAppearance.textColor
                wrapMode: Text.Wrap
            }

            ScrollView {
                id: updateNotes
                objectName: "updateNotes"
                visible: updatePrompt.notes !== ""
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredHeight: notesText.implicitHeight + topPadding + bottomPadding
                Layout.minimumHeight: 0
                padding: 14
                clip: true
                contentWidth: availableWidth
                contentHeight: notesText.implicitHeight
                ScrollBar.horizontal.policy: ScrollBar.AlwaysOff
                ScrollBar.vertical: ThemedScrollBar { policy: ScrollBar.AsNeeded }

                background: Rectangle {
                    radius: 12
                    color: NumaAppearance.rosePine ? NumaAppearance.overlayColor : "#0fffffff"
                }

                Column {
                    id: notesText
                    objectName: "updateNoteBlocks"
                    width: updateNotes.contentWidth
                    spacing: 8

                    Repeater {
                        model: updatePrompt.notes === "" ? [] : updatePrompt.noteBlocks

                        Text {
                            required property string modelData
                            width: notesText.width
                            text: modelData
                            textFormat: Text.MarkdownText
                            color: NumaAppearance.textColor
                            wrapMode: Text.Wrap
                            font.family: "Inter"
                            font.pixelSize: 14
                        }
                    }
                }
            }

            GridLayout {
                id: updateActions
                Layout.alignment: Qt.AlignRight
                columns: updatePrompt.availableWidth >= laterButton.implicitWidth + installButton.implicitWidth + columnSpacing ? 2 : 1
                columnSpacing: 8
                rowSpacing: 6

                SettingsButton {
                    id: laterButton
                    Layout.fillWidth: updateActions.columns === 1
                    text: qsTr("Later")
                    onClicked: updatePrompt.close()
                }
                SettingsButton {
                    id: installButton
                    objectName: "updateInstallButton"
                    Layout.fillWidth: updateActions.columns === 1
                    prominent: true
                    enabled: !updatePrompt.downloading && !updatePrompt.authorizing
                    text: updatePrompt.readyToInstall ? qsTr("Restart to update")
                        : NumaUpdates.installsInApp ? qsTr("Update and restart") : qsTr("Download installer")
                    onClicked: {
                        if (updatePrompt.readyToInstall) NumaUpdates.restartToUpdate();
                        else NumaUpdates.downloadInstaller();
                        if (!NumaUpdates.installsInApp) updatePrompt.close();
                    }
                }
            }
        }
    }

    // Shows the notes once after an update. A first run only records the newest release.
    function maybeOpenReleases() {
        const newest = NumaReleaseNotes.length > 0 ? NumaReleaseNotes[0].version : "";
        if (!overlays.signedIn || newest === "" || NumaAppearance.lastSeenRelease === newest)
            return;
        if (!NumaAppearance.welcomeSeen) {
            NumaAppearance.welcomeSeen = true;
            NumaAppearance.lastSeenRelease = newest;
        } else {
            overlays.openReleases("");
        }
    }

    function maybeOpenOnLaunch() {
        overlays.maybeOpenReleases();
    }

    onSignedInChanged: overlays.maybeOpenOnLaunch()
    Component.onCompleted: overlays.maybeOpenOnLaunch()

    function openSettings() {
        settingsLoader.active = true;
        Qt.callLater(() => {
            if (settingsLoader.item)
                settingsLoader.item.open();
        });
    }

    function openDocs(page) {
        docsLoader.page = page || "";
        docsLoader.active = true;
        Qt.callLater(() => {
            if (docsLoader.item)
                docsLoader.item.open();
        });
    }

    function openReleases(version) {
        releasesLoader.release = version || "";
        releasesLoader.active = true;
        Qt.callLater(() => {
            if (releasesLoader.item)
                releasesLoader.item.open();
        });
    }

    // Docs and release notes are reading popups.
    function readerOpen() {
        return overlays.overlayOpen(docsLoader) || overlays.overlayOpen(releasesLoader);
    }

    function openSupport(reportId) {
        const closing = overlays.overlayOpen(settingsLoader) || overlays.overlayOpen(spotlightLoader) || overlays.readerOpen();
        supportHost.open(reportId || "", closing);
    }

    function openSpotlight(query) {
        spotlightLoader.active = true;
        Qt.callLater(() => {
            if (spotlightLoader.item)
                spotlightLoader.item.openSpotlight(query || "");
        });
    }

    function overlayOpen(loader) {
        return loader.item && loader.item.visible;
    }

    Loader {
        id: settingsLoader
        active: false

        sourceComponent: Component {
            SettingsPopup {
                backdrop: overlays.stage
                onClosed: settingsLoader.active = false
                onReleaseNotesRequested: {
                    overlays.openReleases("");
                    close();
                }
            }
        }
    }

    Loader {
        id: docsLoader
        // Page id to open on; set before the loader activates.
        property string page: ""
        active: false

        sourceComponent: Component {
            DocsPopup {
                backdrop: overlays.stage
                page: docsLoader.page
                onClosed: docsLoader.active = false
            }
        }
    }

    Loader {
        id: releasesLoader
        // Version to open on; set before the loader activates.
        property string release: ""
        active: false

        sourceComponent: Component {
            ReleasesPopup {
                backdrop: overlays.stage
                release: releasesLoader.release
                onClosed: releasesLoader.active = false
            }
        }
    }

    Loader {
        id: spotlightLoader
        active: false

        sourceComponent: Component {
            SpotlightSearch {
                backdrop: overlays.stage
                onClosed: spotlightLoader.active = false
                onPageRequested: function(page) {
                    overlays.shell.navigateTo(page);
                }
                onSearchRequested: function(query) {
                    overlays.shell.searchQuery = query;
                    overlays.stage.topBar.query = query;
                    overlays.shell.history = [];
                    overlays.shell.currentItem = ({
                    });
                    overlays.shell.currentPage = "search";
                    NumaSearch.search(query, "all");
                }
                onDetailRequested: function(page, item) {
                    overlays.shell.openDetail(page, item);
                }
                onSongRequested: function(song) {
                    NumaPlayback.playSong(song);
                }
                onUnsupportedRequested: function(media) {
                    NumaMusicVideo.playVideo(media);
                }
                onSettingsRequested: overlays.openSettings()
                onDocsRequested: overlays.openDocs()
                onReleasesRequested: overlays.openReleases("")
                onSupportRequested: overlays.openSupport("")
                onQueueRequested: overlays.shell.queueOpen = true
            }
        }
    }

    SupportHost {
        id: supportHost
        backdrop: overlays.stage
        page: overlays.shell.currentPage
        z: 200
        onNotice: message => overlays.shell.showNotice(message)
    }

    // The sacred '/' shortcut. Don't trigger if the user is actually trying to type
    // AC/DC in an input box unless you want an angry mob with pitchforks.
    Shortcut {
        sequence: "/"
        enabled: overlays.shell.visible && !overlays.welcomeOpen && !overlays.shell.fullscreenOpen && !overlays.overlayOpen(spotlightLoader) && !overlays.overlayOpen(settingsLoader) && !overlays.readerOpen() && !supportHost.popupOpen && !overlays.shell.isEditableFocused()
        onActivated: overlays.openSpotlight("")
    }

    // Ctrl+K for the terminal-jockeys and VS Code muscle-memory victims.
    Shortcut {
        sequence: "Ctrl+K"
        enabled: overlays.shell.visible && !overlays.welcomeOpen && !overlays.shell.fullscreenOpen && !overlays.overlayOpen(spotlightLoader) && !overlays.overlayOpen(settingsLoader) && !overlays.readerOpen() && !supportHost.popupOpen
        onActivated: overlays.openSpotlight("")
    }

    // Space: because reaching for the mouse to pause when someone walks in is high-stakes cardio.
    Shortcut {
        sequence: "Space"
        autoRepeat: false
        enabled: overlays.shell.visible && !NumaMusicVideo.open && !overlays.welcomeOpen && !overlays.overlayOpen(spotlightLoader) && !overlays.overlayOpen(settingsLoader) && !overlays.readerOpen() && !supportHost.popupOpen && !overlays.shell.isEditableFocused()
        onActivated: {
            NumaPlayback.toggle();
            overlays.shell.wakeFullscreen();
        }
    }

    // F for fullscreen. Also what you pay respects to when the bridge hits.
    Shortcut {
        sequence: "F"
        autoRepeat: false
        enabled: overlays.shell.visible && !overlays.welcomeOpen && !NumaMusicVideo.open && Boolean(NumaPlayback.track.id) && !overlays.overlayOpen(spotlightLoader) && !overlays.overlayOpen(settingsLoader) && !overlays.readerOpen() && !supportHost.popupOpen && !overlays.shell.isEditableFocused()
        onActivated: overlays.shell.setFullscreen(!overlays.shell.fullscreenOpen)
    }

    // F11 for universal native full screen toggle
    Shortcut {
        sequence: "F11"
        autoRepeat: false
        enabled: overlays.shell.visible && !NumaMusicVideo.open && !overlays.shell.isEditableFocused()
        onActivated: {
            const win = overlays.Window.window;
            if (!win)
                return;
            if (win.visibility === Window.FullScreen)
                win.showMaximized();
            else
                win.showFullScreen();
        }
    }

    Shortcut {
        sequence: "Escape"
        enabled: overlays.shell.fullscreenOpen && !NumaMusicVideo.open && !overlays.overlayOpen(settingsLoader) && !overlays.readerOpen() && !supportHost.popupOpen
        onActivated: overlays.shell.setFullscreen(false)
    }

    Shortcut {
        sequence: "Left"
        enabled: overlays.shell.visible && !overlays.welcomeOpen && !overlays.overlayOpen(spotlightLoader) && !overlays.overlayOpen(settingsLoader) && !overlays.readerOpen() && !supportHost.popupOpen && !overlays.shell.isEditableFocused()
        onActivated: {
            NumaPlayback.seekBy(-5);
            overlays.shell.wakeFullscreen();
        }
    }

    Shortcut {
        sequence: "Right"
        enabled: overlays.shell.visible && !overlays.welcomeOpen && !overlays.overlayOpen(spotlightLoader) && !overlays.overlayOpen(settingsLoader) && !overlays.readerOpen() && !supportHost.popupOpen && !overlays.shell.isEditableFocused()
        onActivated: {
            NumaPlayback.seekBy(5);
            overlays.shell.wakeFullscreen();
        }
    }
}
