import ".."
import "../settings"
import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs

Row {
    id: root
    property var media: ({})
    property int buttonWidth: 40
    property int buttonHeight: 40
    property bool roundButtons: false
    property color accentColor: "#b79bed"
    readonly property var sync: typeof NumaSync === "undefined" ? null : NumaSync
    readonly property string mediaId: String(kind === "playlist" ? media.playlistId || media.browseId || media.id || "" : media.id || media.browseId || "")
    readonly property string kind: media.type || "playlist"
    readonly property bool saved: sync && sync.revision >= 0 && sync.isSaved(kind, mediaId)
    visible: !!sync && mediaId.length > 0
    spacing: buttonWidth < 40 ? 6 : 8

    component Action: CollectionActionButton {
        width: root.buttonWidth
        height: root.buttonHeight
        padding: (width - 18) / 2
        round: root.roundButtons
        accentColor: root.accentColor
    }
    component Entry: MenuItem {
        id: entry
        implicitHeight: 36
        leftPadding: 12
        rightPadding: 12
        indicator: null
        background: RoundedSurfaceFrame {
            radius: 6
            softGlow: entry.highlighted
            showBorder: entry.highlighted
            fillColor: entry.highlighted ? "#16ffffff" : "transparent"
            border.color: entry.highlighted ? "#32ffffff" : "transparent"
        }
        contentItem: Item {
            implicitHeight: 24
            Text {
                anchors.left: parent.left
                anchors.right: marker.left
                anchors.verticalCenter: parent.verticalCenter
                text: entry.text
                color: entry.enabled ? NumaAppearance.textColor : NumaAppearance.mutedColor
                font.family: "Inter"
                font.pixelSize: 13
                elide: Text.ElideRight
            }
            LucideIcon {
                id: marker
                anchors.right: parent.right
                anchors.verticalCenter: parent.verticalCenter
                width: 16; height: 16
                name: "check"
                visible: entry.checked
                color: NumaAppearance.textColor
            }
        }
    }

    Action {
        objectName: "cloudLibraryButton"
        glyph: root.saved ? "check" : "library-big"
        text: root.saved ? qsTr("Remove from NÜMA library") : qsTr("Save to NÜMA library")
        onClicked: root.sync.toggleSaved(root.kind, Object.assign({}, root.media, {id: root.mediaId}))
    }
    Action {
        id: coverButton
        objectName: "playlistArtworkButton"
        visible: root.kind === "playlist"
        glyph: "image"
        text: qsTr("Playlist artwork")
        onClicked: coverMenu.popup(coverButton, 0, coverButton.height + 6)
        Menu {
            id: coverMenu
            objectName: "playlistArtworkMenu"
            width: 270
            padding: 5
            popupType: Popup.Item
            font.family: "Inter"
            font.pixelSize: 13
            palette.text: NumaAppearance.textColor
            palette.windowText: NumaAppearance.textColor
            background: PopupBloomSurface { radius: 12 }
            Entry { objectName: "playlistArtworkChange"; text: qsTr("Change artwork…"); onTriggered: chooser.open() }
            Entry {
                id: automaticItem
                objectName: "playlistArtworkAutomatic"
                text: qsTr("Automatic artwork")
                checkable: true
                checked: root.sync && root.sync.revision >= 0 && root.sync.automaticCover(root.mediaId)
                onTriggered: root.sync.setAutomaticCover(root.mediaId, checked)
                GlassToolTip { control: automaticItem; text: qsTr("Follows songs played from this playlist.") }
            }
            Entry {
                text: qsTr("Remove artwork")
                onTriggered: {
                    root.sync.clearCover(root.mediaId)
                    if (root.media.source === "local" || root.mediaId.startsWith("local-playlist:")) NumaLocal.clearPlaylistCover(root.mediaId)
                }
            }
            MenuSeparator {}
            Entry { text: qsTr("Remove from application"); onTriggered: removal.open() }
        }
    }
    FileDialog {
        id: chooser
        title: qsTr("Choose playlist artwork")
        nameFilters: [qsTr("Images (*.png *.jpg *.jpeg *.webp)")]
        onAccepted: { crop.imageSource = selectedFile; crop.open() }
    }
    PlaylistCoverCropDialog {
        id: crop
        objectName: "playlistCropDialog"
        onCropAccepted: area => {
            if (root.sync.setCoverCrop(root.mediaId, crop.imageSource, area)) crop.accept()
            else crop.errorText = root.sync.coverError || qsTr("The artwork could not be saved. Try again.")
        }
    }
    ConfirmDialog {
        id: removal
        title: qsTr("Remove “%1” from NÜMA?").arg(root.media.title || "")
        message: qsTr("The playlist will leave this library. Your songs and the playlist on YouTube Music are kept.")
        confirmText: qsTr("Remove")
        destructive: true
        onAccepted: {
            const id = root.mediaId
            const local = root.media.source === "local" || id.startsWith("local-playlist:")
            root.sync.removePlaylist(id)
            if (local) NumaLocal.deletePlaylist(id)
        }
    }
}
