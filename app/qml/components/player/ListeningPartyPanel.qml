import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Numa

ColumnLayout {
    id: root
    spacing: 20
    readonly property var party: typeof NumaParty !== "undefined" ? NumaParty : null
    readonly property bool active: party !== null && party.active
    readonly property color ink: NumaAppearance.textColor
    readonly property color muted: NumaAppearance.mutedColor
    readonly property color accent: NumaAppearance.accentColor

    component PartyButton: Button {
        id: button
        property bool primary: false
        implicitHeight: 44
        leftPadding: 18
        rightPadding: 18
        font.family: "Inter"
        font.pixelSize: 13
        font.weight: Font.DemiBold
        opacity: enabled ? 1 : 0.45
        contentItem: Text {
            text: button.text
            font: button.font
            color: button.primary ? "#191724" : root.ink
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            elide: Text.ElideRight
        }
        background: Rectangle {
            radius: height / 2
            color: button.primary ? root.accent : button.down ? "#25ffffff" : button.hovered ? "#18ffffff" : "#0cffffff"
            border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : button.primary ? "transparent" : "#25ffffff"
            border.width: NumaAppearance.controlBorderWidth
        }
    }

    RowLayout {
        spacing: 12
        LucideIcon { name: "circle-user-round"; color: root.accent; Layout.preferredWidth: 26; Layout.preferredHeight: 26 }
        ColumnLayout {
            Layout.fillWidth: true
            spacing: 5
            Text { text: qsTr("Shared rooms"); color: root.ink; font.pixelSize: 20; font.weight: Font.DemiBold }
            RowLayout {
                visible: root.active
                spacing: 6
                Rectangle { width: 6; height: 6; radius: 3; color: root.party && root.party.connected ? "#9ccfd8" : root.muted }
                Text {
                    text: root.party && root.party.connected ? qsTr("Connected") : qsTr("Connecting…")
                    color: root.muted
                    font.pixelSize: 12
                }
            }
        }
    }
    Text {
        Layout.fillWidth: true
        visible: !root.active
        wrapMode: Text.WordWrap
        text: qsTr("Listen together on PC or Android.")
        color: root.muted
        font.pixelSize: 13
    }
    PartyButton {
        visible: !root.active
        Layout.fillWidth: true
        primary: true
        text: qsTr("Create room")
        onClicked: root.party.create()
    }
    ColumnLayout {
        visible: !root.active
        Layout.fillWidth: true
        spacing: 10
        Text { text: qsTr("Or join with a code"); color: root.muted; font.pixelSize: 12 }
        RowLayout {
            spacing: 10
            TextField {
                id: roomCode
                objectName: "partyRoomCode"
                Layout.fillWidth: true
                maximumLength: 6
                inputMethodHints: Qt.ImhUppercaseOnly | Qt.ImhNoPredictiveText
                font.family: "Consolas"
                font.pixelSize: 17
                font.letterSpacing: text.length ? 4 : 0
                horizontalAlignment: TextInput.AlignHCenter
                placeholderText: "ABC234"
                color: root.ink
                placeholderTextColor: root.muted
                selectByMouse: true
                onTextEdited: {
                    const normalized = text.toUpperCase().replace(/[^A-Z2-9]/g, "").slice(0, 6);
                    if (normalized !== text) {
                        const position = cursorPosition;
                        text = normalized;
                        cursorPosition = Math.min(position, text.length);
                    }
                }
                onAccepted: if (text.length === 6) root.party.join(text)
                implicitHeight: 44
                background: Rectangle { radius: 12; color: "#0cffffff"; border.width: NumaAppearance.controlBorderWidth; border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : roomCode.activeFocus ? root.accent : "#25ffffff" }
            }
            PartyButton { text: qsTr("Join"); enabled: roomCode.text.length === 6; onClicked: root.party.join(roomCode.text) }
        }
    }
    ColumnLayout {
        visible: root.active
        Layout.fillWidth: true
        spacing: 18
        Rectangle {
            Layout.fillWidth: true
            implicitHeight: 112
            radius: 16
            color: NumaAppearance.rosePine ? "#26233a" : "#0cffffff"
            border.color: NumaAppearance.rosePine ? NumaAppearance.borderColor : "#18ffffff"
            border.width: NumaAppearance.controlBorderWidth
            ColumnLayout {
                anchors.centerIn: parent
                spacing: 8
                Text { Layout.alignment: Qt.AlignHCenter; text: qsTr("INVITATION CODE"); color: root.muted; font.pixelSize: 10; font.letterSpacing: 1.4 }
                RowLayout {
                    spacing: 10
                    TextEdit {
                        readOnly: true
                        selectByMouse: true
                        text: root.party && root.party.code ? root.party.code.toUpperCase() : "······"
                        color: root.ink
                        font.family: "Consolas"
                        font.letterSpacing: 5
                        font.pixelSize: 30
                        font.bold: true
                    }
                    ToolButton {
                        id: copy
                        implicitWidth: 34
                        implicitHeight: 34
                        enabled: root.party && root.party.connected
                        Accessible.name: qsTr("Copy code")
                        onClicked: { root.party.copyCode(); copyFeedback.restart(); }
                        contentItem: LucideIcon { name: copyFeedback.running ? "check" : "copy"; color: root.accent }
                        background: Rectangle { radius: 17; color: copy.hovered ? "#18ffffff" : "transparent" }
                        Timer { id: copyFeedback; interval: 1400 }
                        ToolTip.visible: hovered
                        ToolTip.text: qsTr("Copy code")
                    }
                }
            }
        }
        RowLayout {
            Layout.fillWidth: true
            Text { Layout.fillWidth: true; text: qsTr("Participants"); color: root.ink; font.pixelSize: 13; font.weight: Font.DemiBold }
            Text { text: root.party && root.party.host ? qsTr("You are the host") : qsTr("Following the host"); color: root.muted; font.pixelSize: 11 }
        }
        Repeater {
            model: root.party ? root.party.peers : []
            delegate: RowLayout {
                required property var modelData
                Layout.fillWidth: true
                spacing: 12
                Rectangle {
                    width: 34; height: 34; radius: 17; color: "#20c4a7e7"
                    Text { anchors.centerIn: parent; text: (modelData.name || "?").slice(0, 1).toUpperCase(); color: root.accent; font.pixelSize: 14 }
                }
                Text { Layout.fillWidth: true; text: modelData.name || qsTr("Participant"); color: root.ink; elide: Text.ElideRight; font.pixelSize: 13 }
                PartyButton { visible: root.party && root.party.host; implicitHeight: 32; text: qsTr("Give control"); onClicked: root.party.transferHost(modelData.id) }
            }
        }
        Text { visible: root.party && root.party.peers.length === 0; text: qsTr("Share the code to invite someone."); color: root.muted; font.pixelSize: 12; wrapMode: Text.WordWrap; Layout.fillWidth: true }
        Rectangle { Layout.fillWidth: true; implicitHeight: 1; color: "#18ffffff" }
        PartyButton {
            Layout.fillWidth: true
            text: root.party && root.party.host ? qsTr("Close room") : qsTr("Leave room")
            onClicked: root.party.leave()
        }
    }
    Text { Layout.fillWidth: true; visible: text.length > 0; text: root.party ? root.party.message : ""; color: "#ebbcba"; wrapMode: Text.WordWrap; font.pixelSize: 12 }
}
