import QtQuick
import QtQuick.Controls

ScrollBar {
    id: root

    width: 10
    padding: 2
    minimumSize: 0.06
    contentItem: Rectangle {
        implicitWidth: 6
        implicitHeight: 32
        radius: 3
        color: NumaAppearance.rosePine ? NumaAppearance.accentColor : NumaAppearance.mutedColor
        opacity: root.size < 1 ? (root.active || root.hovered || root.pressed ? 0.95 : 0.65) : 0
    }
    background: Item {}
}
