// SPDX-License-Identifier: AGPL-3.0-or-later

pragma ComponentBehavior: Bound

import Numa
import QtQuick

Loader {
    id: root

    property bool loading: false
    property bool lyrics: false
    property bool trackRows: false
    property bool showAlbum: false
    readonly property color ink: NumaAppearance.textColor
    readonly property color fill: Qt.rgba(ink.r, ink.g, ink.b, NumaAppearance.rosePine ? 0.12 : 0.13)
    readonly property color detailFill: Qt.rgba(ink.r, ink.g, ink.b, NumaAppearance.rosePine ? 0.19 : 0.20)
    readonly property int contentHeight: trackRows ? 180 : lyrics ? 106 : 126
    property bool delayed: false

    active: loading && visible && delayed
    height: active ? contentHeight : 0
    onLoadingChanged: delayed = false
    onVisibleChanged: if (!visible) delayed = false

    Timer {
        interval: 300
        running: root.loading && root.visible && !root.delayed
        onTriggered: root.delayed = true
    }

    sourceComponent: trackRows ? trackLayout : textLayout

    Component {
        id: trackLayout

        Column {
            Repeater {
                model: [0.58, 0.74, 0.46]

                Item {
                    id: trackRow
                    objectName: "skeletonTrackRow"
                    required property real modelData
                    readonly property real albumWidth: root.showAlbum ? Math.round(width * 0.26) : 0
                    width: root.width
                    height: 60

                    Rectangle {
                        x: 23
                        anchors.verticalCenter: parent.verticalCenter
                        width: 10
                        height: 6
                        radius: 3
                        color: root.fill
                    }

                    Rectangle {
                        objectName: "skeletonTrackArtwork"
                        x: 58
                        anchors.verticalCenter: parent.verticalCenter
                        width: 44
                        height: 44
                        radius: 7
                        color: root.fill
                    }

                    Column {
                        id: titleBlock
                        x: 116
                        width: Math.max(0, trackRow.width - x - 108
                            - (root.showAlbum ? trackRow.albumWidth + 16 : 0))
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 6

                        Rectangle {
                            width: Math.min(310, titleBlock.width * trackRow.modelData)
                            height: 10
                            radius: 4
                            color: root.detailFill
                        }

                        Rectangle {
                            width: Math.min(180, titleBlock.width * trackRow.modelData * 0.59)
                            height: 7
                            radius: 3
                            color: root.fill
                        }
                    }

                    Rectangle {
                        x: trackRow.width - 108 - trackRow.albumWidth
                        anchors.verticalCenter: parent.verticalCenter
                        visible: root.showAlbum
                        width: Math.min(132, trackRow.albumWidth * 0.55)
                        height: 7
                        radius: 3
                        color: root.fill
                    }

                    Rectangle {
                        x: trackRow.width - 68
                        anchors.verticalCenter: parent.verticalCenter
                        width: 24
                        height: 7
                        radius: 3
                        color: root.fill
                    }
                }
            }
        }
    }

    Component {
        id: textLayout

        Column {
            spacing: root.lyrics ? 14 : 9

            Repeater {
                model: root.lyrics ? [0.75, 0.92, 0.66, 0.83] : [0.60, 0.76, 0.50]

                Rectangle {
                    id: bar
                    required property real modelData
                    width: root.lyrics ? root.width * modelData : root.width
                    height: root.lyrics ? 16 : 36
                    radius: root.lyrics ? 5 : 9
                    color: root.fill

                    Rectangle {
                        visible: !root.lyrics
                        x: 14
                        width: Math.max(0, (bar.width - 28) * bar.modelData)
                        height: 8
                        anchors.verticalCenter: parent.verticalCenter
                        radius: 3
                        color: root.detailFill
                    }
                }
            }
        }
    }
}
