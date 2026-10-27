#include "account/cloud_json.h"
#include <QSet>
#include <QJsonDocument>

namespace {
QJsonObject byId(const QJsonArray &rows) {
    QJsonObject result;
    for (const auto &value : rows) { auto row = value.toObject(); auto id = row.value("id").toString(); if (!id.isEmpty()) result.insert(id, row); }
    return result;
}
QJsonArray orderedValues(QJsonObject map, const QJsonArray &order, bool days = false) {
    QJsonArray out;
    for (auto value : order) {
        const auto row = value.toObject();
        const auto key = days ? row.value("date").toString() + "|" + row.value("trackId").toString() : row.value("id").toString();
        if (map.contains(key)) { out.append(map.take(key)); }
    }
    for (auto it = map.begin(); it != map.end(); ++it) out.append(it.value());
    return out;
}
QJsonObject dayMap(const QJsonArray &rows) {
    QJsonObject out;
    for (auto value : rows) { auto row = value.toObject(); out.insert(row.value("date").toString() + "|" + row.value("trackId").toString(), row); }
    return out;
}
}
namespace cloud {
QString playlistId(const QString &id) {
    auto key = id;
    if (key.startsWith("offline-playlist:")) key = key.mid(17);
    if (key.startsWith("VL")) key = key.mid(2);
    return key == "LM" ? QStringLiteral("FEmusic_liked_videos") : key;
}
QVariantList visibleMedia(const QVariantList &items, const QJsonArray &hidden, bool playlists) {
    QSet<QString> ids; for (auto value : hidden) ids.insert(playlistId(value.toString()));
    QVariantList out;
    for (const auto &value : items) {
        const auto row = value.toMap();
        const bool playlist = playlists || row.value("type") == "playlist" || row.value("kind") == "playlist";
        QString id;
        for (const auto &key : {"playlistId", "browseId", "id"}) if (id.isEmpty()) id = row.value(key).toString();
        if (id.isEmpty()) id = row.value("browsePayload").toMap().value("browseId").toString();
        if (!playlist || !ids.contains(playlistId(id))) out.append(value);
    }
    return out;
}
QVariantList visibleSections(const QVariantList &sections, const QJsonArray &hidden) {
    QVariantList out;
    for (const auto &value : sections) {
        auto section = value.toMap();
        const auto items = visibleMedia(section.value("items").toList(), hidden, section.value("key") == "playlists");
        if (items.isEmpty()) continue;
        section.insert("items", items); out.append(section);
    }
    return out;
}
QJsonObject restoreDefaultPlaylists(const QJsonObject &library) {
    auto out = library;
    QJsonArray hidden;
    for (auto value : library.value("hiddenPlaylistIds").toArray()) {
        const auto id = playlistId(value.toString());
        if (id != "FEmusic_liked_videos" && id != "SE") hidden.append(value);
    }
    out.insert("hiddenPlaylistIds", hidden);
    return out;
}
QJsonObject automaticArtwork(const QJsonObject &automatic, const QVariantMap &track) {
    const auto origin = playlistId(track.value("queueOrigin").toMap().value("id").toString());
    const auto art = fromDesktop(track, "song").value("artworkUrl").toString();
    auto out = automatic;
    if (!origin.isEmpty() && automatic.value("mode:" + origin).toBool() && art.startsWith("https://")) out.insert("art:" + origin, art);
    return out;
}
QString field(const QString &kind) {
    if (kind == "artist" || kind == "artists") return "savedArtists";
    if (kind == "album" || kind == "albums") return "savedAlbums";
    if (kind == "playlist" || kind == "playlists") return "savedPlaylists";
    return "likedTracks";
}
QJsonObject mergeMap(const QJsonObject &base, const QJsonObject &local, const QJsonObject &remote) {
    auto out = remote;
    QSet<QString> keys;
    for (auto it = base.begin(); it != base.end(); ++it) keys.insert(it.key());
    for (auto it = local.begin(); it != local.end(); ++it) keys.insert(it.key());
    for (const auto &key : keys) if (base.value(key) != local.value(key)) {
        if (local.contains(key)) out.insert(key, local.value(key)); else out.remove(key);
    }
    return out;
}
QJsonObject reconcile(const QJsonObject &sent, const QJsonObject &local, const QJsonObject &remote) {
    auto out = remote;
    for (const auto *key : {"likedTracks", "savedArtists", "savedAlbums", "savedPlaylists", "playedTracks", "recentlyPlayed"})
        out.insert(key, orderedValues(mergeMap(byId(sent.value(key).toArray()), byId(local.value(key).toArray()), byId(remote.value(key).toArray())), remote.value(key).toArray()));
    for (const auto *key : {"coverStyles", "automaticCovers"}) out.insert(key, mergeMap(sent.value(key).toObject(), local.value(key).toObject(), remote.value(key).toObject()));
    auto asMap = [](const QJsonArray &ids) { QJsonObject map; for (auto id : ids) map.insert(playlistId(id.toString()), true); return map; };
    auto hidden = mergeMap(asMap(sent.value("hiddenPlaylistIds").toArray()), asMap(local.value("hiddenPlaylistIds").toArray()), asMap(remote.value("hiddenPlaylistIds").toArray()));
    QJsonArray hiddenIds;
    for (auto value : remote.value("hiddenPlaylistIds").toArray()) {
        const auto id = playlistId(value.toString());
        if (hidden.contains(id)) { hiddenIds.append(id); hidden.remove(id); }
    }
    for (auto it = hidden.begin(); it != hidden.end(); ++it) hiddenIds.append(it.key());
    out.insert("hiddenPlaylistIds", hiddenIds);
    return out;
}
QJsonArray mergeRows(const QJsonObject &base, const QJsonObject &local, const QJsonArray &remote) {
    return orderedValues(mergeMap(base, local, byId(remote)), remote);
}
QJsonObject countersDelta(const QJsonObject &local, const QJsonObject &sent) {
    QJsonObject out;
    for (const auto *key : {"counts", "heard"}) {
        QJsonObject map, a = local.value(key).toObject(), b = sent.value(key).toObject();
        for (auto it = a.begin(); it != a.end(); ++it) map.insert(it.key(), qMax(0., it.value().toDouble() - b.value(it.key()).toDouble()));
        out.insert(key, map);
    }
    QJsonArray days;
    auto old = dayMap(sent.value("days").toArray());
    for (auto value : local.value("days").toArray()) {
        auto row = value.toObject(), previous = old.value(row.value("date").toString() + "|" + row.value("trackId").toString()).toObject();
        for (const auto *key : {"listens", "heardMs"}) row.insert(key, qMax(0., row.value(key).toDouble() - previous.value(key).toDouble()));
        days.append(row);
    }
    out.insert("days", days); return out;
}
QJsonObject addPending(const QJsonObject &snapshot, const QJsonObject &pending) {
    auto out = snapshot, counts = snapshot.value("playCounts").toObject();
    const auto delta = pending.value("counts").toObject();
    for (auto it = delta.begin(); it != delta.end(); ++it) counts.insert(it.key(), counts.value(it.key()).toDouble() + it.value().toDouble());
    out.insert("playCounts", counts);
    double total = snapshot.value("listeningTotalMs").toDouble();
    const auto heard = pending.value("heard").toObject(); for (auto it = heard.begin(); it != heard.end(); ++it) total += it.value().toDouble();
    out.insert("listeningTotalMs", total);
    auto days = dayMap(snapshot.value("listeningDays").toArray());
    for (auto value : pending.value("days").toArray()) {
        auto row = value.toObject(); const auto key = row.value("date").toString() + "|" + row.value("trackId").toString(); auto old = days.value(key).toObject();
        for (const auto *field : {"listens", "heardMs"}) row.insert(field, row.value(field).toDouble() + old.value(field).toDouble());
        days.insert(key, row);
    }
    out.insert("listeningDays", orderedValues(days, snapshot.value("listeningDays").toArray(), true)); return out;
}
QJsonObject fromDesktop(const QVariantMap &media, const QString &kind) {
    QJsonObject out;
    auto text = [&media](const char *key) { return media.value(QString::fromLatin1(key)).toString(); };
    auto id = text("id"); if (id.isEmpty()) id = text("browseId"); if (id.isEmpty()) id = text("playlistId");
    out.insert("id", kind == "playlist" ? playlistId(id) : id);
    out.insert("title", text("title")); out.insert("artworkUrl", text("thumbnail").isEmpty() ? text("artworkUrl") : text("thumbnail"));
    if (kind == "artist") { out.insert("name", text("title").isEmpty() ? text("name") : text("title")); out.remove("title"); out.insert("subtitle", text("subtitle")); }
    else if (kind == "playlist") { out.insert("author", text("author").isEmpty() ? text("subtitle") : text("author")); out.insert("description", text("description")); }
    else { out.insert("artist", text("artist").isEmpty() ? text("subtitle") : text("artist")); out.insert("album", text("album")); out.insert("albumId", text("albumId")); out.insert("artistId", text("artistId")); }
    out.insert("explicit", media.value("explicit").toBool());
    if (kind == "song") out.insert("durationMs", qint64(media.value("durationSeconds").toDouble() * 1000));
    else if (kind != "artist") { QJsonArray tracks; for (const auto &track : media.value("tracks").toList()) tracks.append(fromDesktop(track.toMap(), "song")); out.insert("tracks", tracks); out.insert("year", text("year")); }
    return out;
}
QVariantMap toDesktop(const QJsonObject &media, const QString &kind) {
    auto out = media.toVariantMap(); out.insert("type", kind); out.insert("thumbnail", media.value("artworkUrl").toString());
    if (kind == "artist") out.insert("title", media.value("name").toString());
    out.insert("subtitle", media.value("artist").toString(media.value("author").toString(media.value("subtitle").toString())));
    out.insert("durationSeconds", media.value("durationMs").toDouble() / 1000.);
    QVariantList artists; for (auto value : media.value("artists").toArray()) artists.append(value.isObject() ? value.toObject().value("name").toString() : value.toString());
    out.insert("artists", artists);
    if (kind == "playlist" || kind == "album") {
        QVariantList tracks; for (const auto value : media.value("tracks").toArray()) tracks.append(toDesktop(value.toObject(), "song"));
        out.insert("tracks", tracks); out.insert("playlistId", media.value("id").toString());
        out.insert("totalTrackCount", tracks.size());
        const auto id = media.value("id").toString();
        const auto browseId = kind == "playlist" && !id.startsWith("FE") && !id.startsWith("VL") ? "VL" + id : id;
        out.insert("browseId", browseId); out.insert("browsePayload", QVariantMap{{"browseId", browseId}});
    }
    return out;
}
}
