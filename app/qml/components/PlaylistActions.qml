import Numa
import QtQuick
import QtQuick.Controls
import "settings"

Menu {
    id: root
    property var playlist: ({})
    readonly property string playlistId: String(playlist.playlistId || playlist.id || playlist.browseId
                                                || (playlist.browsePayload || {}).browseId || "").replace(/^VL/, "")
    readonly property bool local: playlist.source === "local" || playlistId.startsWith("local-playlist:")
    readonly property bool canDelete: local || (playlistId.startsWith("PL")
                                               && NumaAuth.isSignedIn && !NumaNetwork.offline)
    width: 210
    padding: 5
    background: PopupBloomSurface { radius: 12 }
    Component.onCompleted: if ("popupType" in root) root.popupType = 0

    function confirmDelete() {
        if (!canDelete)
            return;
        confirmation.createObject(Overlay.overlay, {
            targetId: playlistId, targetTitle: playlist.title || "", localPlaylist: local
        }).open();
    }

    MenuItem {
        id: remove
        text: qsTr("Delete playlist")
        enabled: root.canDelete
        implicitHeight: 36
        onTriggered: root.confirmDelete()
        background: RoundedSurfaceFrame {
            radius: 6
            softGlow: remove.highlighted
            showBorder: remove.highlighted
            fillColor: remove.highlighted ? "#16ffffff" : "transparent"
        }
        contentItem: Text { text: remove.text; color: remove.enabled ? "#f2f0eb" : "#7c857f"; font.pixelSize: 13; verticalAlignment: Text.AlignVCenter }
    }

    Component {
        id: confirmation
        ConfirmDialog {
            property string targetId
            property string targetTitle
            property bool localPlaylist
            title: qsTr("Delete “%1”?").arg(targetTitle)
            message: localPlaylist ? qsTr("Your songs stay on your computer. Only the playlist goes away.")
                                   : qsTr("This deletes the playlist from your YouTube Music account. Songs are not deleted.")
            confirmText: qsTr("Delete")
            destructive: true
            onAccepted: {
                if (localPlaylist)
                    NumaLocal.deletePlaylist(targetId);
                else
                    NumaLibrary.deletePlaylist(targetId, targetTitle);
            }
            onClosed: destroy()
        }
    }
}
