import QtQuick
import "home"

Rectangle {
    id: root
    property var media: ({})
    property string artwork: media.thumbnail || ""
    property string customArtwork: ""
    property bool fallbackGenerated: false
    readonly property string identity: String(media.playlistId || media.browseId || media.id || "")
        .replace(/^offline-playlist:/, "").replace(/^VL/, "")
    readonly property string kind: {
        const title = String(media.title || "").toLowerCase();
        if (["LM", "FEmusic_liked_videos"].includes(identity) || /^(liked music|liked songs|your likes|música que te gusta|música que me gusta)$/.test(title)) return "liked";
        if (identity === "SE" || /episodes.*later|episodios.*tarde/.test(title)) return "episodes";
        return media.source === "local" || identity.startsWith("local-playlist:") ? "local" : "playlist";
    }
    readonly property bool generated: !customArtwork &&
        (kind === "liked" || kind === "episodes" || (kind === "local" && !media.hasCustomCover && !media.hasAutomaticCover) || fallbackGenerated || !artwork)
    readonly property bool dawn: NumaAppearance.theme === "rose_pine_dawn"
    readonly property bool rose: NumaAppearance.rosePine
    readonly property color ink: rose ? (dawn ? "#faf4ed" : "#191724") : "#ffffff"
    readonly property color coverColor: rose
        ? kind === "liked" ? (dawn ? "#b4637a" : "#eb6f92")
        : kind === "episodes" ? (dawn ? "#286983" : "#9ccfd8")
        : (dawn ? "#907aa9" : "#c4a7e7")
        : kind === "local" ? "#7950dc" : "#9464ee"
    readonly property color gradientEnd: rose
        ? kind === "liked" ? (dawn ? "#d7827e" : "#ebbcba")
        : kind === "episodes" ? (dawn ? "#56949f" : "#31748f")
        : (dawn ? "#b4637a" : "#eb6f92")
        : kind === "local" ? "#35c8ed" : "#f54fa1"

    // The immersive background also needs an image for covers drawn by QML.
    readonly property string backgroundArtwork: generated
        ? "data:image/svg+xml;base64," + Qt.btoa('<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"><defs><linearGradient id="cover" x1="0" y1="0" x2="0" y2="1"><stop stop-color="' + coverColor + '"/><stop offset="1" stop-color="' + gradientEnd + '"/></linearGradient></defs><rect width="512" height="512" fill="url(#cover)"/></svg>')
        : customArtwork || artwork

    radius: 12
    color: generated ? coverColor : "transparent"
    antialiasing: true
    gradient: generated ? coverGradient : null
    Gradient {
        id: coverGradient
        GradientStop { position: 0; color: root.coverColor }
        GradientStop { position: 1; color: root.gradientEnd }
    }
    LucideIcon {
        anchors.centerIn: parent
        width: Math.min(parent.width, parent.height) * 0.4
        height: width
        name: root.kind === "liked" ? "heart-filled" : root.kind === "episodes" ? "mic-vocal" : "list-music"
        color: root.ink
        visible: root.generated
    }
    RoundedArtwork {
        anchors.fill: parent
        radius: root.radius
        source: root.generated ? "" : root.customArtwork || root.artwork
        visible: !root.generated
    }
}
