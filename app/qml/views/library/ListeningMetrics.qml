import QtQuick
import QtQuick.Controls
import Numa
import "../../components"
import "../../components/home"

ScrollView {
    id: root
    property int period: 0
    property string query: ""
    property int refreshTick: 0
    Timer { interval: 15000; repeat: true; running: root.visible; onTriggered: root.refreshTick++ }
    readonly property var metrics: {
        const tick = refreshTick;
        const revision = NumaSync.revision;
        return NumaSync.listeningMetrics(period);
    }
    signal trackRequested(var track, var tracks)
    clip: true
    contentWidth: availableWidth
    function positionViewAtBeginning() { contentItem.contentY = 0; }
    function filtered(rows) {
        const needle = query.trim().toLowerCase();
        return (rows || []).filter(row => !needle || [row.title, row.artist, row.album].join(" ").toLowerCase().includes(needle));
    }
    Column {
        width: root.availableWidth
        spacing: 20
        Row {
            spacing: 8
            Repeater {
                model: [7, 30, 365, 0]
                LibraryPill {
                    required property int modelData
                    text: modelData === 0 ? qsTr("All time") : qsTr("%1 days").arg(modelData)
                    checked: root.period === modelData
                    onClicked: root.period = modelData
                }
            }
        }
        Row {
            width: parent.width
            spacing: 12
            Repeater {
                model: [
                    {icon: "audio-lines", title: qsTr("Listens"), value: String(root.metrics.listens || 0)},
                    {icon: "clock", title: qsTr("Listening time"), value: qsTr("%1 h %2 min").arg(Math.floor((root.metrics.heardMs || 0) / 3600000)).arg(Math.floor((root.metrics.heardMs || 0) / 60000) % 60)},
                    {icon: "music-2", title: qsTr("Songs heard"), value: String(root.metrics.songs || 0)}
                ]
                Rectangle {
                    required property var modelData
                    width: Math.min(360, (parent.width - 24) / 3)
                    height: 96
                    radius: 10
                    color: NumaAppearance.rosePine ? NumaAppearance.overlayColor : "#12ffffff"
                    Rectangle {
                        x: 16; anchors.verticalCenter: parent.verticalCenter
                        width: 42; height: 42; radius: 10
                        color: NumaAppearance.rosePine ? NumaAppearance.highlightColor : "#18ffffff"
                        LucideIcon { anchors.centerIn: parent; width: 22; height: 22; name: modelData.icon; color: NumaAppearance.accentColor }
                    }
                    Column {
                        x: 72; width: parent.width - 88
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 6
                        Text { width: parent.width; text: modelData.value; color: NumaAppearance.textColor; font.pixelSize: 26; font.bold: true; fontSizeMode: Text.Fit; minimumPixelSize: 16; elide: Text.ElideRight }
                        Text { width: parent.width; text: modelData.title; color: NumaAppearance.mutedColor; font.pixelSize: 13; elide: Text.ElideRight }
                    }
                }
            }
        }
        Text {
            visible: (root.metrics.listens || 0) === 0
            text: qsTr("Songs appear here after 15 seconds of listening.")
            color: NumaAppearance.mutedColor
            wrapMode: Text.Wrap
            width: parent.width
        }
        Repeater {
            model: [
                {title: qsTr("Most played songs"), key: "tracks"},
                {title: qsTr("Most played artists"), key: "artists"},
                {title: qsTr("Most played albums"), key: "albums"}
            ]
            Column {
                id: group
                required property var modelData
                property bool expanded: false
                readonly property var rows: root.filtered(root.metrics[modelData.key])
                width: parent.width
                spacing: 8
                visible: rows.length > 0
                property int selectedPeriod: root.period
                property string selectedQuery: root.query
                onSelectedPeriodChanged: expanded = false
                onSelectedQueryChanged: expanded = false
                Text { text: group.modelData.title; color: NumaAppearance.textColor; font.pixelSize: 18; font.bold: true }
                Repeater {
                    model: group.expanded ? group.rows : group.rows.slice(0, 5)
                    Rectangle {
                        id: entry
                        required property var modelData
                        required property int index
                        width: group.width
                        height: 64
                        radius: 10
                        color: pointer.containsMouse ? (NumaAppearance.theme === "rose_pine_dawn" ? "#ede8e4" : "#16ffffff") : "transparent"
                        Text { x: 12; anchors.verticalCenter: parent.verticalCenter; text: entry.index + 1; color: NumaAppearance.accentColor; font.bold: true }
                        RoundedArtwork {
                            id: portrait
                            x: 40; anchors.verticalCenter: parent.verticalCenter
                            width: 46; height: 46; radius: group.modelData.key === "artists" ? 23 : 8
                            fallbackSources: {
                                const id = String(entry.modelData.id || "");
                                return group.modelData.key === "tracks" && /^[A-Za-z0-9_-]{11}$/.test(id)
                                    ? ["https://i.ytimg.com/vi/" + id + "/mqdefault.jpg"] : [];
                            }
                            source: {
                                const revision = NumaSync.revision;
                                if (group.modelData.key === "artists")
                                    return NumaSync.metricArtistArtwork(entry.modelData.title || "");
                                const artwork = entry.modelData.thumbnail || entry.modelData.artworkUrl || entry.modelData.coverUrl || "";
                                if (artwork)
                                    return artwork;
                                const id = String(entry.modelData.id || "");
                                return group.modelData.key === "tracks" && /^[A-Za-z0-9_-]{11}$/.test(id)
                                    ? "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg" : "";
                            }
                        }
                        Text {
                            x: 40; width: 46; anchors.verticalCenter: parent.verticalCenter
                            visible: group.modelData.key === "artists" && !portrait.hasImage
                            text: String(entry.modelData.title || "").slice(0, 1).toUpperCase()
                            horizontalAlignment: Text.AlignHCenter
                            color: NumaAppearance.accentColor
                            font.pixelSize: 23; font.bold: true
                        }
                        Column {
                            x: 100; width: Math.max(0, parent.width - 220)
                            anchors.verticalCenter: parent.verticalCenter
                            spacing: 4
                            Text { width: parent.width; text: entry.modelData.title || ""; elide: Text.ElideRight; color: NumaAppearance.textColor; font.pixelSize: 14; font.bold: true }
                            Text { width: parent.width; text: group.modelData.key === "tracks" ? (entry.modelData.artist || "") : qsTr("%1 songs").arg(entry.modelData.songCount); elide: Text.ElideRight; color: NumaAppearance.mutedColor; font.pixelSize: 12 }
                        }
                        Text { anchors.right: parent.right; anchors.rightMargin: 14; anchors.verticalCenter: parent.verticalCenter; text: qsTr("%1 listens").arg(entry.modelData.listenCount); color: NumaAppearance.accentColor; font.pixelSize: 13 }
                        MouseArea {
                            id: pointer
                            anchors.fill: parent
                            hoverEnabled: true
                            cursorShape: group.modelData.key === "tracks" ? Qt.PointingHandCursor : Qt.ArrowCursor
                            onClicked: if (group.modelData.key === "tracks") root.trackRequested(entry.modelData, group.rows)
                        }
                    }
                }
                LibraryPill {
                    visible: group.rows.length > 5
                    text: group.expanded ? qsTr("Show less") : qsTr("Show more")
                    onClicked: group.expanded = !group.expanded
                }
            }
        }
        Item { width: 1; height: 100 }
    }
}
