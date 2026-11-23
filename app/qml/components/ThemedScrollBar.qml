import QtQuick
import QtQuick.Controls

ScrollBar {
    id: root

    property color thumbColor: NumaAppearance.rosePine ? NumaAppearance.accentColor
        : root.Window.window && root.Window.window.playbackAccentColor !== undefined
            ? root.Window.window.playbackAccentColor : NumaAppearance.mutedColor

    width: 10
    padding: 2
    minimumSize: 0.06
    contentItem: Rectangle {
        implicitWidth: 6
        implicitHeight: 32
        radius: 3
        color: root.thumbColor
        opacity: root.size < 1 ? (root.active || root.hovered || root.pressed ? 0.95 : 0.65) : 0
    }
    background: Item {}
}
