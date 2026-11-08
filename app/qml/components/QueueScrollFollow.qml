import QtQuick
import QtQuick.Controls

QtObject {
    id: root
    required property ListView view
    property bool active: true
    property bool continuous: false
    property int currentIndex: -1
    property bool dragging: false
    property int resumeDelay: 3000
    property bool following: true
    property bool restoring: false
    property real savedOffset: 0
    readonly property bool interacting: dragging || view.dragging || view.flicking || view.ScrollBar.vertical.pressed

    function resumeLater() {
        if (active && continuous && !interacting && !restoring)
            resumeTimer.restart();
    }
    function hold() {
        following = false;
        centerAnimation.stop();
        resumeTimer.stop();
        resumeLater();
    }
    function preserve() {
        savedOffset = view.contentY - view.originY;
        restoring = true;
        hold();
    }
    function restore() {
        Qt.callLater(() => {
            view.forceLayout();
            const bottom = Math.max(view.originY, view.originY + view.contentHeight - view.height);
            view.contentY = Math.max(view.originY, Math.min(bottom, view.originY + savedOffset));
            restoring = false;
            resumeLater();
        });
    }
    function orderChanged() {
        if (!restoring) {
            following = true;
            Qt.callLater(() => center());
        }
    }
    function center(animated = true) {
        if (!active || !continuous || interacting || restoring || currentIndex < 0 || currentIndex >= view.count)
            return;
        resumeTimer.stop();
        centerAnimation.stop();
        following = true;
        view.forceLayout();
        const start = view.contentY;
        view.positionViewAtIndex(Math.max(0, currentIndex - 2), ListView.Beginning);
        const target = view.contentY;
        if (animated && Math.abs(target - start) > 1) {
            view.contentY = start;
            centerAnimation.to = target;
            centerAnimation.restart();
        }
    }
    onInteractingChanged: interacting ? hold() : resumeLater()
    onActiveChanged: {
        if (!active) {
            resumeTimer.stop();
            centerAnimation.stop();
        } else {
            following = true;
            Qt.callLater(() => center(false));
        }
    }
    onContinuousChanged: {
        if (!continuous) resumeTimer.stop();
        else {
            following = true;
            Qt.callLater(() => center(false));
        }
    }

    readonly property Timer resumeTimer: Timer {
        interval: root.resumeDelay
        onTriggered: root.center()
    }
    readonly property NumberAnimation centerAnimation: NumberAnimation {
        target: root.view
        property: "contentY"
        duration: 350
        easing.type: Easing.OutCubic
    }
    readonly property Connections movement: Connections {
        target: root.view
        function onModelChanged() {
            if (root.following && !root.restoring)
                Qt.callLater(() => root.center(false));
        }
        function onContentYChanged() {
            if (!root.following && !root.restoring && !root.centerAnimation.running)
                root.resumeLater();
        }
    }
}
