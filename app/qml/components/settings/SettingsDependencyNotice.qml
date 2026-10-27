import QtQuick
import QtQuick.Layouts
import ".."

Rectangle {
    id: root

    property string title: qsTr("Why is this unavailable?")
    property string message
    property color accentColor: NumaAppearance.rosePine ? NumaAppearance.borderColor : "#a8cdb6"

    Layout.fillWidth: true
    implicitHeight: content.implicitHeight + 24
    radius: 12
    color: Qt.rgba(accentColor.r, accentColor.g, accentColor.b, 0.09)
    border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : Qt.rgba(accentColor.r, accentColor.g, accentColor.b, 0.28)
    border.width: NumaAppearance.controlBorderWidth

    RowLayout {
        id: content
        anchors.fill: parent
        anchors.margins: 12
        spacing: 10

        LucideIcon {
            Layout.preferredWidth: 18
            Layout.preferredHeight: 18
            Layout.alignment: Qt.AlignTop
            name: "info"
            color: root.accentColor
        }

        ColumnLayout {
            Layout.fillWidth: true
            spacing: 3

            Text {
                Layout.fillWidth: true
                text: root.title
                color: "#eef4ef"
                font.family: "Inter"
                font.pixelSize: 13
                font.weight: Font.DemiBold
            }

            Text {
                Layout.fillWidth: true
                text: root.message
                color: "#b7c5ba"
                font.family: "Inter"
                font.pixelSize: 12
                wrapMode: Text.WordWrap
            }
        }
    }
}
