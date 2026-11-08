import QtQuick

HoverHandler {
    id: cursor
    cursorShape: Qt.PointingHandCursor
    enabled: parent && parent.enabled
    property Item tint: null
    property Component tintFactory: Component {
        HoverTint { control: cursor.parent; hovered: cursor.hovered }
    }
    onHoveredChanged: {
        if (hovered && !tint && parent && "background" in parent && "pressed" in parent)
            tint = tintFactory.createObject(parent);
    }
}
