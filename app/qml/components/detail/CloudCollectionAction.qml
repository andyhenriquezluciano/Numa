import Numa
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
    readonly property bool localPlaylist: kind === "playlist" && (media.source === "local" || mediaId.startsWith("local-playlist:"))
    readonly property bool localSaved: localPlaylist && typeof NumaLocal !== "undefined"
        && (NumaLocal.playlists || []).some(playlist => String(playlist.id || "") === mediaId)
    readonly property bool saved: localPlaylist ? localSaved : sync && sync.revision >= 0 && sync.isSaved(kind, mediaId)
    visible: (localPlaylist || !!sync) && mediaId.length > 0
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
            fillColor: entry.highlighted || entry.hovered ? (NumaAppearance.rosePine ? NumaAppearance.highlightColor : "#16ffffff") : "transparent"
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
        enabled: !root.localPlaylist || root.localSaved
        glyph: root.saved ? "check" : "library-big"
        text: root.saved ? qsTr("Remove from NÜMA library") : qsTr("Save to NÜMA library")
        onClicked: {
            if (root.saved && root.kind === "playlist")
                removal.open();
            else
                root.sync.toggleSaved(root.kind, Object.assign({}, root.media, {id: root.mediaId}));
        }
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
            Entry { objectName: "playlistArtworkChange"; text: qsTr("Change artwork…"); onTriggered: root.localPlaylist ? LocalPickers.pickCover("playlist", root.mediaId) : chooser.open() }
            Entry {
                id: automaticItem
                objectName: "playlistArtworkAutomatic"
                text: qsTr("Automatic artwork")
                checkable: true
                checked: root.localPlaylist ? Boolean(root.media.automaticCover)
                    : root.sync && root.sync.revision >= 0 && root.sync.automaticCover(root.mediaId)
                onTriggered: root.localPlaylist ? NumaLocal.setAutomaticCover(root.mediaId, checked, NumaPlayback.track)
                    : root.sync.setAutomaticCover(root.mediaId, checked)
                GlassToolTip { control: automaticItem; text: qsTr("Follows songs played from this playlist.") }
            }
            Entry {
                text: qsTr("Remove artwork")
                onTriggered: {
                    if (root.localPlaylist) NumaLocal.clearPlaylistCover(root.mediaId)
                    else root.sync.clearCover(root.mediaId)
                }
            }

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
        objectName: "removePlaylistConfirmation"
        title: qsTr("Remove “%1” from NÜMA?").arg(root.media.title || "")
        message: root.localPlaylist
            ? qsTr("This local playlist and its songs will be removed from NÜMA, including other local playlists that contain them. Your audio files will stay on this computer.")
            : qsTr("The playlist will leave this library. Your songs and the playlist on YouTube Music are kept.")
        confirmText: qsTr("Remove")
        destructive: true
        onAccepted: {
            const id = root.mediaId;
            if (root.localPlaylist)
                NumaLocal.deletePlaylist(id, true);
            else
                root.sync.removePlaylist(id);
        }
    }
}
