/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

#include "lyrics/lyrics_controller.h"
#include "lyrics/lyrics_identity.h"
#include "offline/connectivity_monitor.h"
#include "offline/download_manager.h"
#include "offline/offline_store.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QTemporaryDir>
#include <QTimer>
#include <QtTest>

namespace {

struct FakeNetwork {
  QList<bool> answers;
  int calls{0};
  bool next() {
    const int index = qMin(calls++, static_cast<int>(answers.size()) - 1);
    return answers.at(index);
  }
  ConnectivityMonitor::Probe probe() {
    return [this](std::function<void(bool)> done) {
      const bool ok = next();
      QTimer::singleShot(0, [done = std::move(done), ok] { done(ok); });
    };
  }
};

ConnectivityMonitor::Timing fastTiming() {
  ConnectivityMonitor::Timing timing;
  timing.graceMs = 120;
  timing.pollMs = 100000;
  timing.tickMs = 10;
  timing.reportDebounceMs = 0;
  timing.systemHints = false;
  return timing;
}

QString writeBytes(const QString &path, const QByteArray &bytes) {
  QDir().mkpath(QFileInfo(path).absolutePath());
  QFile file(path);
  if (file.open(QIODevice::WriteOnly))
    file.write(bytes);
  return path;
}

QJsonObject sampleLyricsObject() {
  QJsonArray lines;
  lines.append(QJsonObject{{QStringLiteral("text"), QStringLiteral("Is this the real life?")},
                           {QStringLiteral("startTime"), 1.5},
                           {QStringLiteral("endTime"), 4.0}});
  lines.append(QJsonObject{{QStringLiteral("text"), QStringLiteral("Is this just fantasy?")},
                           {QStringLiteral("startTime"), 4.5},
                           {QStringLiteral("endTime"), 7.0}});
  return QJsonObject{{QStringLiteral("status"), QStringLiteral("ready")},
                     {QStringLiteral("mode"), QStringLiteral("synced")},
                     {QStringLiteral("source"), QStringLiteral("amlyrics")},
                     {QStringLiteral("lines"), lines}};
}

offline::TrackRecord sampleRecord(const QString &id, const QString &dir) {
  offline::TrackRecord track;
  track.id = id;
  track.meta = QJsonObject{{QStringLiteral("id"), id},
                           {QStringLiteral("type"), QStringLiteral("song")},
                           {QStringLiteral("title"), id},
                           {QStringLiteral("artist"), QStringLiteral("Artist")},
                           {QStringLiteral("album"), QStringLiteral("Album")},
                           {QStringLiteral("durationSeconds"), 200}};
  track.path = writeBytes(dir + QStringLiteral("/audio/") + id + QStringLiteral(".webm"), QByteArray(1024, 'a'));
  track.mimeType = QStringLiteral("audio/webm");
  track.quality = QStringLiteral("high");
  track.bitrate = 128000;
  track.bytes = 1024;
  track.downloadedAt = 1000;
  return track;
}

} // namespace

class OfflineLyricsTest : public QObject {
  Q_OBJECT

private slots:
  void onlineCacheUsesCurrentProviderPolicyWithoutDeletingLegacyLyrics() {
    QTemporaryDir dir;
    offline::OfflineStore store(dir.path());
    auto track = sampleRecord(QStringLiteral("cachedSong"), dir.path());
    track.lyricsPath = writeBytes(store.lyricsDir() + QStringLiteral("/cachedSong.json"), QJsonDocument(sampleLyricsObject()).toJson());
    store.put(track);
    QVERIFY(store.save());
    DownloadManager downloads(nullptr, nullptr, dir.path());
    LyricsController lyrics(nullptr, nullptr, nullptr);
    lyrics.setDownloadManager(&downloads);
    lyrics.request(track.meta.toVariantMap());
    QCOMPARE(lyrics.status(), QStringLiteral("unavailable"));
    QVERIFY(QFileInfo::exists(track.lyricsPath));
    auto refreshed = sampleLyricsObject();
    refreshed.insert(QStringLiteral("policyVersion"), QStringLiteral("recording-timing-v3"));
    refreshed.insert(QStringLiteral("source"), QStringLiteral("lrclib"));
    refreshed = lyrics::validated(refreshed, lyrics::identity(lyrics::lookupTrack(track.meta.toVariantMap())));
    QVERIFY(downloads.saveLyricsForTrack(track.id, refreshed));
    lyrics.request(track.meta.toVariantMap());
    QCOMPARE(lyrics.status(), QStringLiteral("ready"));
    QCOMPARE(lyrics.source(), QStringLiteral("lrclib"));
  }

  void validationExpiresAndChangesWithRecording() {
    const QVariantMap track{{"id", "queue-id"}, {"title", "Song"}, {"artist", "Artist"}, {"durationSeconds", 208}};
    const auto key = lyrics::identity(lyrics::lookupTrack(track));
    auto data = sampleLyricsObject();
    data.insert("policyVersion", QString::fromLatin1(lyrics::policyVersion));
    data = lyrics::validated(data, key);
    QVERIFY(lyrics::current(data, key));
    auto changed = track;
    changed.insert("sourceVideoId", "resolved-id");
    changed.insert("sourceDurationSeconds", 211);
    QVERIFY(!lyrics::current(data, lyrics::identity(lyrics::lookupTrack(changed))));
    QVERIFY(!lyrics::current(data, lyrics::identity(lyrics::lookupTrack(track, 210.901))));
    data.insert("validatedAt", QDateTime::currentMSecsSinceEpoch() - 8LL * 24 * 60 * 60 * 1000);
    QVERIFY(!lyrics::current(data, key));
  }

  void storeSavesAndLoadsLyrics() {
    QTemporaryDir dir;
    offline::OfflineStore store(dir.path());
    auto track = sampleRecord(QStringLiteral("song1"), dir.path());
    const QByteArray lyricsJson = QJsonDocument(sampleLyricsObject()).toJson(QJsonDocument::Indented);
    const QString lyricsFile = writeBytes(store.lyricsDir() + QStringLiteral("/song1.json"), lyricsJson);
    track.lyricsPath = lyricsFile;
    store.put(track);
    QVERIFY(store.save());

    offline::OfflineStore loaded(dir.path());
    QVERIFY(loaded.load());
    QVERIFY(loaded.tracks.contains(QStringLiteral("song1")));
    const auto loadedTrack = loaded.tracks.value(QStringLiteral("song1"));
    QCOMPARE(loadedTrack.lyricsPath, lyricsFile);
    QVERIFY(QFileInfo::exists(loadedTrack.lyricsPath));
    QCOMPARE(loaded.totalBytes(), track.bytes + QFileInfo(lyricsFile).size());
  }

  void lyricsControllerLoadsSavedLyricsOffline() {
    QTemporaryDir dir;
    offline::OfflineStore store(dir.path());
    auto track = sampleRecord(QStringLiteral("offlineSong"), dir.path());
    const QByteArray lyricsJson = QJsonDocument(sampleLyricsObject()).toJson(QJsonDocument::Indented);
    track.lyricsPath = writeBytes(store.lyricsDir() + QStringLiteral("/offlineSong.json"), lyricsJson);
    store.put(track);
    QVERIFY(store.save());

    FakeNetwork network{{false, false}};
    ConnectivityMonitor monitor(network.probe(), fastTiming());
    monitor.start();
    QTRY_VERIFY(monitor.offline());

    DownloadManager downloads(nullptr, nullptr, dir.path());
    downloads.setConnectivity(&monitor);
    QVERIFY(downloads.isOffline());
    QVERIFY(downloads.isDownloaded(QStringLiteral("offlineSong")));
    QVERIFY(!downloads.lyricsPathFor(QStringLiteral("offlineSong")).isEmpty());

    LyricsController lyrics(nullptr, nullptr, nullptr);
    lyrics.setDownloadManager(&downloads);
    lyrics.request(track.meta.toVariantMap());

    QCOMPARE(lyrics.status(), QStringLiteral("ready"));
    QCOMPARE(lyrics.mode(), QStringLiteral("synced"));
    QCOMPARE(lyrics.source(), QStringLiteral("amlyrics"));
    QCOMPARE(lyrics.lines().size(), 2);
    QCOMPARE(lyrics.indexAt(2.0), 0);
    QCOMPARE(lyrics.indexAt(5.0), 1);
  }

  void backwardCompatibilityLegacyTracks() {
    QTemporaryDir dir;
    // Construct legacy downloads.json without lyrics fields.
    const QJsonObject legacyTrack{{QStringLiteral("id"), QStringLiteral("legacySong")},
                                  {QStringLiteral("meta"), QJsonObject{{QStringLiteral("id"), QStringLiteral("legacySong")},
                                                                      {QStringLiteral("title"), QStringLiteral("Legacy Song")}}},
                                  {QStringLiteral("path"), writeBytes(dir.path() + QStringLiteral("/audio/legacySong.webm"), QByteArray(512, 'z'))},
                                  {QStringLiteral("mimeType"), QStringLiteral("audio/webm")},
                                  {QStringLiteral("quality"), QStringLiteral("high")},
                                  {QStringLiteral("bitrate"), 128000},
                                  {QStringLiteral("bytes"), 512.0},
                                  {QStringLiteral("thumbnail"), QString()},
                                  {QStringLiteral("animated"), QString()},
                                  {QStringLiteral("downloadedAt"), 500.0}};
    const QJsonObject legacyRoot{{QStringLiteral("version"), 1},
                                 {QStringLiteral("tracks"), QJsonArray{legacyTrack}},
                                 {QStringLiteral("collections"), QJsonArray{}}};
    writeBytes(dir.path() + QStringLiteral("/downloads.json"), QJsonDocument(legacyRoot).toJson());

    DownloadManager downloads(nullptr, nullptr, dir.path());
    QVERIFY(downloads.isDownloaded(QStringLiteral("legacySong")));
    QVERIFY(downloads.lyricsPathFor(QStringLiteral("legacySong")).isEmpty());

    FakeNetwork network{{false, false}};
    ConnectivityMonitor monitor(network.probe(), fastTiming());
    monitor.start();
    QTRY_VERIFY(monitor.offline());
    downloads.setConnectivity(&monitor);

    LyricsController lyrics(nullptr, nullptr, nullptr);
    lyrics.setDownloadManager(&downloads);
    lyrics.request(QVariantMap{{QStringLiteral("id"), QStringLiteral("legacySong")}});

    // Old tracks without lyrics report unavailable without error when offline.
    QCOMPARE(lyrics.status(), QStringLiteral("unavailable"));

    // Online resolution populates lyrics for older downloaded tracks.
    QVERIFY(downloads.saveLyricsForTrack(QStringLiteral("legacySong"), sampleLyricsObject()));
    QVERIFY(!downloads.lyricsPathFor(QStringLiteral("legacySong")).isEmpty());

    lyrics.request(QVariantMap{{QStringLiteral("id"), QStringLiteral("legacySong")}});
    QCOMPARE(lyrics.status(), QStringLiteral("ready"));
    QCOMPARE(lyrics.lines().size(), 2);
  }

  void trackWithoutLyricsCompletesNormally() {
    QTemporaryDir dir;
    offline::OfflineStore store(dir.path());
    auto track = sampleRecord(QStringLiteral("noLyricsSong"), dir.path());
    store.put(track);
    QVERIFY(store.save());

    DownloadManager downloads(nullptr, nullptr, dir.path());
    QVERIFY(downloads.isDownloaded(QStringLiteral("noLyricsSong")));
    QVERIFY(downloads.lyricsPathFor(QStringLiteral("noLyricsSong")).isEmpty());
    QVERIFY(QFileInfo::exists(downloads.audioPathFor(QStringLiteral("noLyricsSong"))));

    LyricsController lyrics(nullptr, nullptr, nullptr);
    lyrics.setDownloadManager(&downloads);
    lyrics.request(track.meta.toVariantMap());
    // Missing lyrics results in unavailable status.
    QCOMPARE(lyrics.status(), QStringLiteral("unavailable"));
  }

  void deletionAndSweepCleansLyrics() {
    QTemporaryDir dir;
    offline::OfflineStore store(dir.path());
    auto track1 = sampleRecord(QStringLiteral("songDel"), dir.path());
    const QByteArray lyricsJson = QJsonDocument(sampleLyricsObject()).toJson(QJsonDocument::Indented);
    track1.lyricsPath = writeBytes(store.lyricsDir() + QStringLiteral("/songDel.json"), lyricsJson);
    store.put(track1);

    auto track2 = sampleRecord(QStringLiteral("songKeep"), dir.path());
    track2.lyricsPath = writeBytes(store.lyricsDir() + QStringLiteral("/songKeep.json"), lyricsJson);
    store.put(track2);
    QVERIFY(store.save());

    DownloadManager downloads(nullptr, nullptr, dir.path());
    QVERIFY(QFileInfo::exists(track1.lyricsPath));
    downloads.remove(QStringLiteral("songDel"));
    // Deleting a track deletes its lyrics file.
    QVERIFY(!QFileInfo::exists(track1.lyricsPath));
    QVERIFY(!downloads.isDownloaded(QStringLiteral("songDel")));

    const QString orphan = writeBytes(downloads.store().lyricsDir() + QStringLiteral("/orphan.json"), QByteArray("{\"orphan\":true}"));
    QVERIFY(QFileInfo::exists(orphan));

    // Sweep purges orphan lyrics files while keeping referenced ones.
    QCOMPARE(downloads.store().sweep(), 1);
    QVERIFY(!QFileInfo::exists(orphan));
    QVERIFY(QFileInfo::exists(track2.lyricsPath));

    // Clear all cleans the entire lyrics folder.
    downloads.clearAll();
    QVERIFY(!QFileInfo::exists(track2.lyricsPath));
  }
};

QTEST_MAIN(OfflineLyricsTest)
#include "offline_lyrics_test.moc"
