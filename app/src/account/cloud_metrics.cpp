#include "account/cloud_library.h"
#include "account/cloud_json.h"
#include "playback/playback_controller.h"
#include <QJsonArray>
#include <QDate>

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
    m_data.insert("local", local); m_flush.start();
    if (listen) { ++m_revision; emit changed(); }
    if (!m_debounce.isActive()) m_debounce.start(10000);
}
