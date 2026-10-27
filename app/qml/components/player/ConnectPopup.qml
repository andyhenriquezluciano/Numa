import ".."
import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

GlassPopup {
    id: root
    width: Math.min(420, parent.width - 48)
    height: Math.min(content.implicitHeight + 48, parent.height - 64)
    contentItem: Flickable {
        clip: true
        contentHeight: content.implicitHeight + 48
        boundsBehavior: Flickable.StopAtBounds
        ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }
        ColumnLayout {
            id: content
            x: 24
            y: 24
            width: parent.width - 48
            ListeningPartyPanel { Layout.fillWidth: true }
        }
    }
}
