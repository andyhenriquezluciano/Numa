import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

GridLayout {
    id: root
    readonly property bool signedIn: typeof NumaAccount !== "undefined" && NumaAccount.isSignedIn
    readonly property var sync: typeof NumaSync === "undefined" ? null : NumaSync
    Layout.fillWidth: true
    visible: !!sync
    columns: width >= syncAction.implicitWidth + 200 ? 2 : 1
    columnSpacing: 22
    rowSpacing: 8

    Item {
        id: copySlot
        Layout.fillWidth: true
        implicitHeight: copy.implicitHeight

        ColumnLayout {
            id: copy
            width: Math.min(copySlot.width, 420)
            spacing: 6

            Text {
                Layout.fillWidth: true
                text: root.sync ? root.sync.status : ""
                color: NumaAppearance.textColor
                font.family: "Inter"; font.pixelSize: 12
                wrapMode: Text.Wrap
            }
            Text {
                objectName: "syncDescription"
                Layout.fillWidth: true
                text: qsTr("Your NÜMA account saves favorites, artists, albums, playlists, artwork and listening metrics. Audio files stay on each device.")
                color: "#a4aaa1"; font.pixelSize: 11; wrapMode: Text.Wrap
            }
        }
    }
    RowLayout {
        id: syncAction
        Layout.alignment: Qt.AlignRight | Qt.AlignVCenter
        BusyIndicator { running: root.sync ? root.sync.busy : false; visible: running; implicitWidth: 24; implicitHeight: 24 }
        SettingsButton {
            prominent: true
            objectName: "syncLibraryNow"
            text: root.sync && root.sync.busy ? qsTr("Syncing…") : qsTr("Sync now")
            enabled: root.signedIn && root.sync && !root.sync.busy
            opacity: enabled ? 1 : 0.45
            onClicked: root.sync.syncNow()
        }
    }
}
