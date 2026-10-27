import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

ColumnLayout {
    id: root
    readonly property bool signedIn: typeof NumaAccount !== "undefined" && NumaAccount.isSignedIn
    readonly property var sync: typeof NumaSync === "undefined" ? null : NumaSync
    Layout.fillWidth: true
    visible: !!sync
    spacing: 8
    Text {
        Layout.fillWidth: true
        text: root.sync ? root.sync.status : ""
        color: NumaAppearance.textColor
        font.family: "Inter"; font.pixelSize: 12
        wrapMode: Text.WordWrap
    }
    Text {
        Layout.fillWidth: true
        text: qsTr("Your NÜMA account saves favorites, artists, albums, playlists, artwork and listening metrics. Audio files stay on each device.")
        color: "#a4aaa1"; font.pixelSize: 11; wrapMode: Text.WordWrap
    }
    RowLayout {
        BusyIndicator { running: root.sync ? root.sync.busy : false; visible: running; implicitWidth: 24; implicitHeight: 24 }
        SettingsButton {
            objectName: "syncLibraryNow"
            text: root.sync && root.sync.busy ? qsTr("Syncing…") : qsTr("Sync now")
            enabled: root.signedIn && root.sync && !root.sync.busy
            opacity: enabled ? 1 : 0.45
            onClicked: root.sync.syncNow()
        }
    }
}
