import QtQuick
import QtQuick.Layouts
import QtQuick.Window

RowLayout {
    id: root
    required property var panel
    required property var artworkView
    spacing: 4

    CompactWindowButton {
        colors: root.panel
        glyph: "mic-vocal"
        label: qsTr("Show lyrics")
        active: root.panel.lyricsMode
        onClicked: root.panel.lyricsMode = !root.panel.lyricsMode
    }

    CompactWindowButton {
        colors: root.panel
        glyph: root.Window.window && root.Window.window.visibility === Window.FullScreen ? "minimize" : "maximize"
        label: root.Window.window && root.Window.window.visibility === Window.FullScreen
               ? qsTr("Exit full screen") : qsTr("Full screen")
        onClicked: {
            const window = root.Window.window;
            if (window)
                window.visibility === Window.FullScreen ? window.showNormal() : window.showFullScreen();
        }
    }

    CompactWindowButton {
        colors: root.panel
        glyph: "list-music"
        label: qsTr("Show queue")
        onClicked: root.artworkView.open = false
    }
}
