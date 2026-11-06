#include "account/cloud_library.h"
#include "account/cloud_json.h"
#include "playback/playback_controller.h"
#include <QJsonArray>
#include <QDate>
#include <QSet>
#include <QFile>
#include <QSaveFile>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QUrlQuery>
#include <algorithm>

void CloudLibrary::sample() {
    const auto elapsed = qMin<qint64>(m_elapsed.restart(), 5000);
    if (m_wasPlaying && !m_lastTrack.isEmpty()) {
        m_heard += elapsed; const bool count = !m_counted && m_heard >= 15000;
        if (count) m_counted = true;
        record(m_lastTrack, elapsed, count);
    }
    const auto track = m_playback->shownTrack();
    if (track.value("id") != m_lastTrack.value("id") || track.value("queueOrigin") != m_lastTrack.value("queueOrigin")) updateAutomaticCover(track);
    if (track.value("id") != m_lastTrack.value("id")) { m_heard = 0; m_counted = false; }
    m_lastTrack = track;
    m_wasPlaying = m_playback->shownPlaying() && !m_playback->remoteActive() && !track.value("id").toString().isEmpty();
}
void CloudLibrary::record(const QVariantMap &track, qint64 ms, bool listen) {
    const auto id = track.value("id").toString(); if (id.isEmpty()) return;
    auto counters = m_data.value("counters").toObject(), local = m_data.value("local").toObject();
    auto counts = counters.value("counts").toObject(), heard = counters.value("heard").toObject();
    if (listen) counts.insert(id, counts.value(id).toInteger() + 1);
    heard.insert(id, heard.value(id).toInteger() + ms); counters.insert("counts", counts); counters.insert("heard", heard);
    const auto date = QDate::currentDate().toString(Qt::ISODate);
    QJsonObject delta{{"date", date}, {"trackId", id}, {"listens", listen ? 1 : 0}, {"heardMs", ms}};
    QJsonArray days; bool found = false;
    for (auto value : counters.value("days").toArray()) {
        auto row = value.toObject();
        if (row.value("date") == date && row.value("trackId") == id) {
            row.insert("listens", row.value("listens").toInteger() + (listen ? 1 : 0));
            row.insert("heardMs", row.value("heardMs").toInteger() + ms); found = true;
        }
        days.append(row);
    }
    if (!found) days.append(delta); counters.insert("days", days); m_data.insert("counters", counters);
    QJsonObject pending{{"counts", QJsonObject{{id, listen ? 1 : 0}}}, {"heard", QJsonObject{{id, ms}}}, {"days", QJsonArray{delta}}};
    local = cloud::addPending(local, pending);
    auto row = cloud::fromDesktop(track, "song");
    for (const auto *key : {"playedTracks", "recentlyPlayed"}) {
        QJsonArray tracks; tracks.append(row);
        for (auto value : local.value(key).toArray()) if (value.toObject().value("id") != id && (QString::fromLatin1(key) != "recentlyPlayed" || tracks.size() < 50)) tracks.append(value);
        local.insert(key, tracks);
    }
    m_data.insert("local", local); if (!m_flush.isActive()) m_flush.start();
    if (listen) { ++m_revision; emit changed(); }
    if (!m_metricSync.isActive()) m_metricSync.start();
}

QVariantMap CloudLibrary::listeningMetrics(int days) const {
    const auto local = m_data.value("local").toObject();
    QMap<QString, qint64> counts;
    qint64 heard = 0, listens = 0;
    if (days <= 0) {
        const auto all = local.value("playCounts").toObject();
        for (auto it = all.begin(); it != all.end(); ++it) counts[it.key()] = it.value().toInteger();
        heard = local.value("listeningTotalMs").toInteger();
    } else {
        const auto since = QDate::currentDate().addDays(1 - days);
        for (const auto &value : local.value("listeningDays").toArray()) {
            const auto row = value.toObject();
            const auto date = QDate::fromString(row.value("date").toString(), Qt::ISODate);
            if (!date.isValid() || date < since || date > QDate::currentDate()) continue;
            counts[row.value("trackId").toString()] += row.value("listens").toInteger();
            heard += row.value("heardMs").toInteger();
        }
    }
    for (auto count : counts) listens += count;
    QVariantList tracks;
    QMap<QString, QVariantMap> artists, albums;
    QSet<QString> seen;
    for (const auto &value : local.value("playedTracks").toArray()) {
        auto track = cloud::toDesktop(value.toObject(), "song");
        const auto id = track.value("id").toString();
        const auto count = counts.value(id);
        if (count <= 0 || seen.contains(id)) continue;
        seen.insert(id);
        track.insert("listenCount", count);
        tracks.append(track);
        const auto addGroup = [&](QMap<QString, QVariantMap> &groups, const QString &name) {
            if (name.trimmed().isEmpty()) return;
            auto group = groups.value(name);
            group.insert("title", name);
            group.insert("listenCount", group.value("listenCount").toLongLong() + count);
            group.insert("songCount", group.value("songCount").toInt() + 1);
            if (!group.contains("thumbnail")) group.insert("thumbnail", track.value("thumbnail"));
            groups.insert(name, group);
        };
        auto names = track.value("artists").toStringList();
        if (names.isEmpty()) names.append(track.value("artist").toString());
        names.removeDuplicates();
        for (const auto &name : names) addGroup(artists, name);
        const auto album = track.value("album").toString();
        if (!album.isEmpty()) addGroup(albums, album + QStringLiteral(" \u00b7 ") + track.value("artist").toString());
    }
    const auto ranked = [](QVariantList rows) {
        std::stable_sort(rows.begin(), rows.end(), [](const QVariant &a, const QVariant &b) {
            const auto left = a.toMap(), right = b.toMap();
            const auto lc = left.value("listenCount").toLongLong(), rc = right.value("listenCount").toLongLong();
            return lc == rc ? left.value("title").toString() < right.value("title").toString() : lc > rc;
        });
        return rows;
    };
    QVariantList artistRows, albumRows;
    for (const auto &row : artists) artistRows.append(row);
    for (const auto &row : albums) albumRows.append(row);
    return {{"listens", listens}, {"heardMs", heard}, {"songs", seen.size()},
            {"tracks", ranked(tracks)}, {"artists", ranked(artistRows)}, {"albums", ranked(albumRows)}};
}

QString CloudLibrary::metricArtistArtwork(const QString &name) {
    const auto key = name.trimmed().toCaseFolded();
    if (key.isEmpty()) return {};
    for (const auto &value : m_data.value("local").toObject().value("savedArtists").toArray()) {
        const auto row = value.toObject();
        if (row.value("name").toString().trimmed().toCaseFolded() == key
            && !row.value("artworkUrl").toString().isEmpty()) return row.value("artworkUrl").toString();
    }
    const auto path = m_root + "/metric-artist-images.json";
    if (m_metricArtistImages.isEmpty()) {
        QFile file(path);
        if (file.open(QIODevice::ReadOnly)) m_metricArtistImages = QJsonDocument::fromJson(file.readAll()).object();
    }
    const auto cached = m_metricArtistImages.value(key).toString();
    if (!cached.isEmpty() || m_metricArtistRequests.contains(key)) return cached;
    // Only visible rows request a portrait; avoid retry loops when offline.
    if (m_metricArtistRequests.size() >= 512) return {};
    m_metricArtistRequests.insert(key);
    QUrl url(QStringLiteral("https://www.theaudiodb.com/api/v1/json/123/search.php"));
    QUrlQuery query;
    query.addQueryItem("s", name.trimmed());
    url.setQuery(query);
    QNetworkRequest request(url);
    request.setTransferTimeout(5000);
    auto *reply = m_network.get(request);
    connect(reply, &QNetworkReply::finished, this, [this, reply, key, path] {
        const auto rows = QJsonDocument::fromJson(reply->readAll()).object().value("artists").toArray();
        if (reply->error() == QNetworkReply::NoError) for (const auto &value : rows) {
            const auto row = value.toObject();
            if (row.value("strArtist").toString().trimmed().toCaseFolded() != key) continue;
            const QUrl image(row.value("strArtistThumb").toString());
            if (image.scheme() != "https" || image.host().isEmpty()) continue;
            m_metricArtistImages.insert(key, image.toString());
            QSaveFile file(path);
            if (file.open(QIODevice::WriteOnly)) {
                file.write(QJsonDocument(m_metricArtistImages).toJson(QJsonDocument::Compact));
                file.commit();
            }
            ++m_revision;
            emit changed();
            break;
        }
        reply->deleteLater();
    });
    return {};
}
