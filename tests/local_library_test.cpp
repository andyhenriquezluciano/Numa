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

#include "local/local_collage.h"
#include "local/local_library.h"
#include "local/local_lyrics.h"
#include "local/local_metadata.h"
#include "local/local_track.h"
#include "local/local_store.h"
#include "local/local_jobs.h"
#include "local/local_media_downloads.h"
#include "lyrics/lyrics_controller.h"
#include "lyrics/translation/lyrics_translator.h"
#include <QSettings>

#include <QFile>
#include <QImage>
#include <QJsonDocument>
#include <QProcess>
#include <QSignalSpy>
#include <QTemporaryDir>
#include <QtEndian>
#include <QtTest>

namespace {

// One second of silence at 8 kHz, 16-bit mono: exactly 128 kbps of nothing.
// The quietest song ever recorded, and still better than the b-side.
QString writeWav(const QString &dir, const QString &name) {
  const QString path = dir + QLatin1Char('/') + name;
  QFile file(path);
  if (!file.open(QIODevice::WriteOnly))
    return {};
  const quint32 dataSize = 16000;
  QByteArray header("RIFF");
  const auto le32 = [](quint32 value) {
    QByteArray bytes(4, 0);
    qToLittleEndian(value, bytes.data());
    return bytes;
  };
  const auto le16 = [](quint16 value) {
    QByteArray bytes(2, 0);
    qToLittleEndian(value, bytes.data());
    return bytes;
  };
  header += le32(36 + dataSize) + "WAVEfmt " + le32(16) + le16(1) + le16(1) + le32(8000) + le32(16000) + le16(2) +
            le16(16) + "data" + le32(dataSize);
  file.write(header);
  file.write(QByteArray(static_cast<int>(dataSize), 0));
  return path;
}

QString writePng(const QString &dir, const QString &name, QColor color) {
  QImage image(64, 64, QImage::Format_RGB32);
  image.fill(color);
  const QString path = dir + QLatin1Char('/') + name;
  image.save(path, "PNG");
  return path;
}

bool toolAvailable(const QString &program) {
  QProcess process;
  process.start(program, {QStringLiteral("-version")});
  return process.waitForFinished(5000) && process.exitCode() == 0;
}

} // namespace

class LocalLibraryTest : public QObject {
  Q_OBJECT

  static void settle(LocalLibrary &library) {
    // busyChanged fires on both edges; wait for the quiet one.
    QTRY_VERIFY_WITH_TIMEOUT(!library.busy(), 30000);
  }

private slots:
  void removingPlaylistSongsKeepsOriginalFiles() {
    QTemporaryDir root, music;
    local::LocalStore store(root.path());
    QStringList paths;
    for (const QString &id : {QStringLiteral("a"), QStringLiteral("b"), QStringLiteral("c")}) {
      local::TrackRecord track;
      track.id = "local:" + id;
      track.title = id;
      track.path = writeWav(music.path(), id + ".wav");
      QVERIFY(!track.path.isEmpty());
      paths.append(track.path);
      track.lyricsPath = music.path() + '/' + id + ".lrc";
      QFile lyrics(track.lyricsPath);
      QVERIFY(lyrics.open(QIODevice::WriteOnly));
      lyrics.write("[00:01]Original lyrics");
      paths.append(track.lyricsPath);
      store.tracks.insert(track.id, track);
      store.trackOrder.append(track.id);
    }
    local::PlaylistRecord removed, kept;
    removed.id = "local-playlist:removed";
    removed.trackIds = {"local:a", "local:b"};
    kept.id = "local-playlist:kept";
    kept.trackIds = {"local:a", "local:c"};
    store.playlists = {removed, kept};
    QVERIFY(store.save());
    LocalLibrary library(root.path());
    library.deletePlaylist(removed.id, true);
    QCOMPARE(library.songs().size(), 1);
    QCOMPARE(library.songs().first().toMap().value("id").toString(), QStringLiteral("local:c"));
    QCOMPARE(library.playlists().size(), 1);
    library.openPlaylist(kept.id);
    const auto tracks = library.detail().value("tracks").toList();
    QCOMPARE(tracks.size(), 1);
    QCOMPARE(tracks.first().toMap().value("id").toString(), QStringLiteral("local:c"));
    for (const QString &path : paths)
      QVERIFY2(QFile::exists(path), qPrintable(path));
  }

  void deletingPlaylistNotifiesAfterRemovingIt() {
    QTemporaryDir store;
    LocalLibrary library(store.path());
    const QString id = library.createPlaylist(QStringLiteral("Temporary"), {});
    library.openPlaylist(id);
    QSignalSpy deleted(&library, &LocalLibrary::playlistDeleted);
    bool absentWhenNotified = false;
    connect(&library, &LocalLibrary::playlistDeleted, this, [&](const QString &removedId) {
      absentWhenNotified = removedId == id && library.detail().isEmpty() && library.playlists().isEmpty();
    });
    library.deletePlaylist(id);
    QCOMPARE(deleted.count(), 1);
    QCOMPARE(deleted.first().first().toString(), id);
    QVERIFY(absentWhenNotified);
    library.deletePlaylist(id);
    QCOMPARE(deleted.count(), 1);
  }

  void collageBuildRunsAsABackgroundJob() {
    QTemporaryDir root, art;
    local::LocalStore store(root.path());
    local::TrackRecord song;
    song.id = QStringLiteral("local:test");
    song.title = QStringLiteral("Song");
    song.path = writeWav(root.path(), "song.wav");
    song.coverPath = writePng(art.path(), "cover.png", Qt::red);
    store.tracks.insert(song.id, song);
    store.trackOrder.append(song.id);
    local::PlaylistRecord playlist;
    playlist.id = QStringLiteral("local-playlist:test");
    playlist.title = QStringLiteral("Mix");
    playlist.trackIds.append(song.id);
    store.playlists.append(playlist);
    QVERIFY(store.save());
    LocalLibrary library(root.path());
    library.openPlaylist(playlist.id);
    library.renamePlaylist(playlist.id, "Updated");
    QVERIFY(library.busy());
    settle(library);
    const auto path = QUrl(library.detail().value("thumbnail").toString()).toLocalFile();
    QVERIFY(QFile::exists(path));
    QCOMPARE(QImage(path).size(), QSize(640, 640));
  }

  void trackIdsAreStableAndPrefixed() {
    const QString id = local::trackIdForPath(QStringLiteral("/music/a.mp3"));
    QVERIFY(local::isLocalTrackId(id));
    QCOMPARE(id, local::trackIdForPath(QStringLiteral("/music/../music/a.mp3")));
    QVERIFY(id != local::trackIdForPath(QStringLiteral("/music/b.mp3")));
    QCOMPARE(local::formatDuration(187), QStringLiteral("3:07"));
    QCOMPARE(local::formatDuration(3723), QStringLiteral("1:02:03"));
  }

  void fileNamesStandInForMissingTags() {
    auto metadata = local::metadataFromFileName(QStringLiteral("/m/01 - Daft Punk - Digital Love.mp3"));
    QCOMPARE(metadata.artist, QStringLiteral("Daft Punk"));
    QCOMPARE(metadata.title, QStringLiteral("Digital Love"));
    metadata = local::metadataFromFileName(QStringLiteral("/m/just_a_title.flac"));
    QVERIFY(metadata.artist.isEmpty());
    QCOMPARE(metadata.title, QStringLiteral("just a title"));
  }

  void probeJsonYieldsTagsAndBitrate() {
    const QByteArray json = R"({
      "streams": [{"codec_type": "video", "codec_name": "mjpeg"},
                  {"codec_type": "audio", "codec_name": "flac", "sample_rate": "44100",
                   "channels": 2, "bits_per_raw_sample": "16"}],
      "format": {"duration": "187.5", "bit_rate": "905000",
                 "tags": {"TITLE": "Digital Love", "Artist": "Daft Punk", "album": "Discovery",
                          "LYRICS": "Last night"}}})";
    const auto metadata = local::parseProbe(QJsonDocument::fromJson(json).object(), 0);
    QVERIFY(metadata.probed);
    QCOMPARE(metadata.title, QStringLiteral("Digital Love"));
    QCOMPARE(metadata.artist, QStringLiteral("Daft Punk"));
    QCOMPARE(metadata.album, QStringLiteral("Discovery"));
    QCOMPARE(metadata.lyrics, QStringLiteral("Last night"));
    QCOMPARE(metadata.codec, QStringLiteral("flac"));
    QCOMPARE(metadata.bitrate, 905000);
    QCOMPARE(metadata.bitDepth, 16);
    QCOMPARE(metadata.sampleRate, 44100);
    QCOMPARE(metadata.durationSeconds, 187.5);
  }

  void bitrateFallsBackToFileSize() {
    const QByteArray json = R"({"streams": [{"codec_type": "audio", "codec_name": "mp3"}],
                                "format": {"duration": "100"}})";
    // 1.6 MB over 100 s is 128 kbps.
    const auto metadata = local::parseProbe(QJsonDocument::fromJson(json).object(), 1600000);
    QCOMPARE(metadata.bitrate, 128000);
  }

  void lrcParsesStampsOffsetsAndOrder() {
    const auto result = local::parseLyrics(QStringLiteral(
        "[ar:Someone]\n[offset:500]\n[00:20.00]Second\n[00:01.50][00:10.00]First <00:02.00>word\n"));
    QCOMPARE(result.value("mode").toString(), QStringLiteral("synced"));
    const QVariantList lines = result.value("lines").toList();
    QCOMPARE(lines.size(), 3);
    QCOMPARE(lines[0].toMap().value("text").toString(), QStringLiteral("First word"));
    QCOMPARE(lines[0].toMap().value("startTime").toDouble(), 1.0); // 1.5 s shifted earlier by 0.5 s.
    QCOMPARE(lines[0].toMap().value("endTime").toDouble(), 9.5);
    QCOMPARE(lines[2].toMap().value("startTime").toDouble(), 19.5);
  }

  void enhancedLrcKeepsWordsAndAppliesLateOffsetToRepeatedLines() {
    const auto data = local::parseLyrics(QStringLiteral(
      "[00:10.00][00:30.00]<00:10.00>Hello <00:11.25>world<00:12.00>\n[offset:500]\n"));
    const auto lines = data.value("lines").toList();
    QCOMPARE(lines.size(), 2);
    QCOMPARE(lines.first().toMap().value("text").toString(), QStringLiteral("Hello world"));
    const auto first = lines.first().toMap().value("words").toList();
    const auto repeat = lines.last().toMap().value("words").toList();
    QCOMPARE(first.size(), 2);
    QCOMPARE(first[0].toMap().value("startTime").toDouble(), 9.5);
    QCOMPARE(first[0].toMap().value("endTime").toDouble(), 10.75);
    QCOMPARE(first[1].toMap().value("endTime").toDouble(), 11.5);
    QCOMPARE(repeat[1].toMap().value("startTime").toDouble(), 30.75);
  }

  void plainAndSrtLyricsParse() {
    const auto plain = local::parseLyrics(QStringLiteral("one\n\ntwo\n"));
    QCOMPARE(plain.value("mode").toString(), QStringLiteral("unsynced"));
    QCOMPARE(plain.value("lines").toList().size(), 2);
    const auto srt = local::parseLyrics(QStringLiteral("1\n00:00:01,000 --> 00:00:03,500\nHello\nthere\n\n"));
    QCOMPARE(srt.value("mode").toString(), QStringLiteral("synced"));
    QCOMPARE(srt.value("lines").toList().first().toMap().value("text").toString(), QStringLiteral("Hello there"));
    QCOMPARE(local::parseLyrics(QStringLiteral("  \n")).value("status").toString(), QStringLiteral("unavailable"));
  }

  void collageFillsTheSquare() {
    QImage a(10, 20, QImage::Format_RGB32), b(20, 10, QImage::Format_RGB32);
    a.fill(Qt::red);
    b.fill(Qt::blue);
    QVERIFY(local::buildCollage({}).isNull());
    const QImage single = local::buildCollage({a}, 64);
    QCOMPARE(single.size(), QSize(64, 64));
    QCOMPARE(single.pixelColor(60, 60), QColor(Qt::red));
    const QImage four = local::buildCollage({a, b, b, a, a}, 64);
    QCOMPARE(four.pixelColor(4, 4), QColor(Qt::red));
    QCOMPARE(four.pixelColor(60, 4), QColor(Qt::blue));
    QCOMPARE(four.pixelColor(4, 60), QColor(Qt::blue));
    QCOMPARE(four.pixelColor(60, 60), QColor(Qt::red));
    // Three covers repeat the first one in the last cell.
    const QImage three = local::buildCollage({a, b, b}, 64);
    QCOMPARE(three.pixelColor(60, 60), QColor(Qt::red));
  }

  void localPlaylistCropUsesSelectedAreaAndKeepsTheOriginal() {
    QTemporaryDir root, photos;
    QImage source(200, 100, QImage::Format_RGB32);
    for (int y = 0; y < source.height(); ++y)
      for (int x = 0; x < source.width(); ++x) source.setPixelColor(x, y, x < 100 ? Qt::red : Qt::blue);
    const QString input = photos.filePath(QStringLiteral("wide.png"));
    QVERIFY(source.save(input));
    LocalLibrary library(root.path());
    const QString id = library.createPlaylist(QStringLiteral("Crop"));
    library.openPlaylist(id);
    QVERIFY(library.setPlaylistCoverCrop(id, input, QRectF(0.5, 0, 0.5, 1)));
    QVERIFY(library.detail().value("hasCustomCover").toBool());
    const QString cropped = QUrl(library.detail().value("thumbnail").toString()).toLocalFile();
    const QImage result(cropped);
    QCOMPARE(result.width(), result.height());
    QCOMPARE(result.pixelColor(result.width() / 2, result.height() / 2), QColor(Qt::blue));
    QCOMPARE(QImage(input), source);
    QVERIFY(!library.setPlaylistCoverCrop(id, input, QRectF(2, 2, 1, 1)));
    QCOMPARE(QUrl(library.detail().value("thumbnail").toString()).toLocalFile(), cropped);
    library.clearPlaylistCover(id);
    QVERIFY(QFileInfo::exists(input));
  }

  void automaticLocalCoverFollowsOnlyItsPlaylistAndPersists() {
    QTemporaryDir root, music;
    local::LocalStore store(root.path());
    for (int i = 0; i < 2; ++i) {
      local::TrackRecord track;
      track.path = writeWav(music.path(), QStringLiteral("%1.wav").arg(i));
      track.id = local::trackIdForPath(track.path);
      track.coverPath = writePng(music.path(), QStringLiteral("%1.png").arg(i), i ? Qt::blue : Qt::red);
      store.tracks.insert(track.id, track); store.trackOrder.append(track.id);
    }
    local::PlaylistRecord playlist;
    playlist.id = QStringLiteral("local-playlist:automatic");
    playlist.title = QStringLiteral("Automatic");
    playlist.trackIds = store.trackOrder;
    store.playlists.append(playlist);
    QVERIFY(store.save());
    const auto playing = [&](int index, const QString &origin) {
      return QVariantMap{{QStringLiteral("id"), store.trackOrder.at(index)},
        {QStringLiteral("queueOrigin"), QVariantMap{{QStringLiteral("id"), origin}}}};
    };
    {
      LocalLibrary library(root.path());
      library.openPlaylist(playlist.id);
      library.setAutomaticCover(playlist.id, true, playing(0, playlist.id));
      QVERIFY(library.detail().value("hasAutomaticCover").toBool());
      QCOMPARE(library.detail().value("thumbnail").toString(), QUrl::fromLocalFile(store.tracks.value(store.trackOrder.at(0)).coverPath).toString());
      library.updateAutomaticCover(playing(1, QStringLiteral("local-playlist:other")));
      QCOMPARE(library.detail().value("thumbnail").toString(), QUrl::fromLocalFile(store.tracks.value(store.trackOrder.at(0)).coverPath).toString());
      library.updateAutomaticCover(playing(1, playlist.id));
      QCOMPARE(library.detail().value("thumbnail").toString(), QUrl::fromLocalFile(store.tracks.value(store.trackOrder.at(1)).coverPath).toString());
      settle(library);
    }
    LocalLibrary reopened(root.path());
    reopened.openPlaylist(playlist.id);
    QVERIFY(reopened.detail().value("automaticCover").toBool());
    QCOMPARE(reopened.detail().value("thumbnail").toString(), QUrl::fromLocalFile(store.tracks.value(store.trackOrder.at(1)).coverPath).toString());
    reopened.clearPlaylistCover(playlist.id);
    QVERIFY(!reopened.detail().value("automaticCover").toBool());
    QVERIFY(!reopened.detail().value("hasAutomaticCover").toBool());
    QVERIFY(QFileInfo::exists(store.tracks.value(store.trackOrder.at(0)).coverPath));
  }

  void localDownloadOptionsRequireLyricsForTranslation() {
    QTemporaryDir root;
    QSettings settings;
    const QStringList keys{QStringLiteral("downloads/localLyrics"), QStringLiteral("downloads/localTranslation"), QStringLiteral("downloads/localArtwork")};
    QVariantList previous;
    for (const auto &key : keys) previous.append(settings.value(key));
    LocalLibrary library(root.path());
    LyricsController document(nullptr, nullptr, nullptr);
    LyricsTranslator preferences(&document, nullptr, false);
    LocalMediaDownloads downloads(&library, nullptr, nullptr, nullptr, &preferences);
    downloads.setLyrics(false);
    downloads.setArtwork(false);
    QVERIFY(!downloads.translation());
    downloads.setTranslation(true);
    QVERIFY(downloads.lyrics());
    QVERIFY(downloads.translation());
    downloads.setLyrics(false);
    QVERIFY(!downloads.translation());
    downloads.setArtwork(true);
    QVERIFY(!downloads.lyrics());
    downloads.start();
    QVERIFY(!downloads.busy());
    QCOMPARE(downloads.total(), 0);
    for (int i = 0; i < keys.size(); ++i) {
      if (previous.at(i).isValid()) settings.setValue(keys.at(i), previous.at(i));
      else settings.remove(keys.at(i));
    }
  }

  void localDownloadsSummarizeEachResourceAgainstLibraryTotal() {
    QTemporaryDir root, music;
    local::LocalStore store(root.path());
    for (int i = 0; i < 2; ++i) {
      local::TrackRecord track;
      track.path = writeWav(music.path(), QStringLiteral("%1.wav").arg(i));
      track.id = local::trackIdForPath(track.path);
      track.title = QString::number(i);
      store.tracks.insert(track.id, track);
      store.trackOrder.append(track.id);
    }
    QVERIFY(store.save());
    LocalLibrary library(root.path());
    const QString first = store.trackOrder.first();
    QVERIFY(library.saveDownloadedLyrics(first, {{"status", "ready"}, {"lines", QVariantList{QVariantMap{{"text", "Lyrics"}}}}}));
    const QString path = library.downloadedArtworkPath(first);
    QFile loop(path);
    QVERIFY(loop.open(QIODevice::WriteOnly)); loop.write("loop"); loop.close();
    QVERIFY(library.saveDownloadedArtwork(first, path));
    LyricsController document(nullptr, nullptr, nullptr);
    LyricsTranslator preferences(&document, nullptr, false);
    const QStringList keys{QStringLiteral("downloads/localLyrics"), QStringLiteral("downloads/localTranslation"), QStringLiteral("downloads/localArtwork")};
    QSettings settings;
    QVariantList previous;
    for (const auto &key : keys) previous.append(settings.value(key));
    LocalMediaDownloads downloads(&library, nullptr, nullptr, nullptr, &preferences);
    downloads.setTranslation(false); downloads.setLyrics(true); downloads.setArtwork(true);
    downloads.start();
    QTRY_VERIFY(!downloads.busy());
    QCOMPARE(downloads.message(), QStringLiteral("Downloaded lyrics: 1 of 2 songs.\nDownloaded animated artwork: 1 of 2 songs."));
    QCOMPARE(downloads.completed(), 2);
    for (int i = 0; i < keys.size(); ++i) {
      if (previous.at(i).isValid()) settings.setValue(keys.at(i), previous.at(i));
      else settings.remove(keys.at(i));
    }
  }

  void downloadedLocalExtrasPersistWithoutChangingAudio() {
    QTemporaryDir root, music;
    const QString audio = writeWav(music.path(), QStringLiteral("Song.wav"));
    QFile source(audio);
    QVERIFY(source.open(QIODevice::ReadOnly));
    const QByteArray original = source.readAll();
    source.close();
    local::LocalStore store(root.path());
    local::TrackRecord track;
    track.id = local::trackIdForPath(audio);
    track.path = audio;
    track.title = QStringLiteral("Song");
    store.tracks.insert(track.id, track);
    store.trackOrder.append(track.id);
    QVERIFY(store.save());
    const QVariantMap lyrics{{QStringLiteral("status"), QStringLiteral("ready")},
      {QStringLiteral("mode"), QStringLiteral("synced")},
      {QStringLiteral("lines"), QVariantList{QVariantMap{{QStringLiteral("text"), QStringLiteral("Hello")}, {QStringLiteral("startTime"), 0}}}}};
    {
      LocalLibrary library(root.path());
      QVERIFY(!library.track(track.id).value("hasDownloadedLyrics").toBool());
      QVERIFY(!library.saveDownloadedLyrics(track.id, {}));
      QVERIFY(library.saveDownloadedLyrics(track.id, lyrics));
      QVERIFY(library.track(track.id).value("hasDownloadedLyrics").toBool());
      QCOMPARE(library.lyricsFor(track.id), lyrics);
      auto replacement = lyrics;
      replacement.insert(QStringLiteral("source"), QStringLiteral("Different"));
      QVERIFY(library.saveDownloadedLyrics(track.id, replacement));
      QCOMPARE(library.lyricsFor(track.id), lyrics);
      const QString artPath = library.downloadedArtworkPath(track.id);
      QFile art(artPath);
      QVERIFY(art.open(QIODevice::WriteOnly));
      art.write("test-loop"); art.close();
      QVERIFY(library.saveDownloadedArtwork(track.id, artPath));
      QVERIFY(library.track(track.id).value("hasDownloadedArtwork").toBool());
    }
    LocalLibrary reopened(root.path());
    QVERIFY(reopened.track(track.id).value("hasDownloadedLyrics").toBool());
    QVERIFY(reopened.track(track.id).value("hasDownloadedArtwork").toBool());
    QCOMPARE(reopened.lyricsFor(track.id), lyrics);
    reopened.clearTrackLyrics(track.id);
    QVERIFY(!reopened.track(track.id).value("hasDownloadedLyrics").toBool());
    QVERIFY(source.open(QIODevice::ReadOnly));
    QCOMPARE(source.readAll(), original);
  }

  void importProgressCountsUniqueAudioAndKnownFiles() {
    QTemporaryDir music, store;
    const QString first = writeWav(music.path(), QStringLiteral("First.wav"));
    const QString second = writeWav(music.path(), QStringLiteral("Second.wav"));
    writePng(music.path(), QStringLiteral("Cover.png"), Qt::red);
    const QSet<QString> known{local::trackIdForPath(first), local::trackIdForPath(second)};
    QList<QPair<int, int>> updates;
    const auto result = local::importPaths({music.path(), first}, known, store.path(),
        [&updates](int done, int total) { updates.append({done, total}); });
    QCOMPARE(result.ids.size(), 2);
    QCOMPARE(updates, (QList<QPair<int, int>>{{0, 2}, {1, 2}, {2, 2}}));
  }

  void importProgressStartsImmediatelyAndFinishes() {
    QTemporaryDir music, store;
    LocalLibrary library(store.path());
    bool reachedEnd = false;
    connect(&library, &LocalLibrary::importProgressChanged, &library, [&] {
      if (library.importing() && !library.scanningImport()
          && library.importTotal() == 2 && library.importCompleted() == 2)
        reachedEnd = true;
    });
    library.importFiles({writeWav(music.path(), QStringLiteral("First.wav")),
                         writeWav(music.path(), QStringLiteral("Second.wav"))});
    QVERIFY(library.importing());
    settle(library);
    QVERIFY(reachedEnd);
    QVERIFY(!library.importing());
    library.importFiles({music.path() + QStringLiteral("/missing")});
    settle(library);
    QVERIFY(!library.importing());
    QCOMPARE(library.importTotal(), 0);
  }

  void importProbesBitrateAndKeepsOrder() {
    if (!toolAvailable(local::ffprobeProgram()))
      QSKIP("ffprobe is not installed");
    QTemporaryDir music, store;
    const QString first = writeWav(music.path(), QStringLiteral("Artist One - First.wav"));
    const QString second = writeWav(music.path(), QStringLiteral("Second.wav"));
    {
      LocalLibrary library(store.path());
      const QString id = library.createPlaylist(QStringLiteral("  Road trip "), {first, second});
      QVERIFY(local::isLocalPlaylistId(id));
      settle(library);
      const QVariantMap detail = [&] { library.openPlaylist(id); return library.detail(); }();
      QCOMPARE(detail.value("title").toString(), QStringLiteral("Road trip"));
      const QVariantList tracks = detail.value("tracks").toList();
      QCOMPARE(tracks.size(), 2);
      const QVariantMap song = tracks[0].toMap();
      QCOMPARE(song.value("artist").toString(), QStringLiteral("Artist One"));
      QCOMPARE(song.value("title").toString(), QStringLiteral("First"));
      QCOMPARE(song.value("source").toString(), QStringLiteral("local"));
      QVERIFY(song.value("localBitrate").toInt() > 120000 && song.value("localBitrate").toInt() < 140000);
      QVERIFY(qAbs(song.value("durationSeconds").toDouble() - 1.0) < 0.1);

      library.moveTrack(id, 0, 1);
      QCOMPARE(library.detail().value("tracks").toList()[1].toMap().value("title").toString(), QStringLiteral("First"));
      library.removeTrack(id, 0);
      QCOMPARE(library.detail().value("tracks").toList().size(), 1);
    }
    // A fresh library over the same folder finds everything again.
    LocalLibrary reopened(store.path());
    QCOMPARE(reopened.playlists().size(), 1);
    QCOMPARE(reopened.songs().size(), 2);
  }

  void collageFollowsCoversUntilTheUserPicksOne() {
    if (!toolAvailable(local::ffprobeProgram()))
      QSKIP("ffprobe is not installed");
    QTemporaryDir music, store, art;
    QStringList files;
    for (int i = 0; i < 4; ++i)
      files << writeWav(music.path(), QStringLiteral("t%1.wav").arg(i));
    LocalLibrary library(store.path());
    const QString id = library.createPlaylist(QStringLiteral("Mix"), QVariantList(files.begin(), files.end()));
    settle(library);
    library.openPlaylist(id);
    QVERIFY(library.detail().value("thumbnail").toString().isEmpty()); // No art yet, no collage.

    const QVariantList tracks = library.detail().value("tracks").toList();
    const QStringList colors{QStringLiteral("#ff0000"), QStringLiteral("#00ff00"), QStringLiteral("#0000ff"),
                             QStringLiteral("#ffff00")};
    for (int i = 0; i < 4; ++i) {
      library.setTrackCover(tracks[i].toMap().value("id").toString(),
                            writePng(art.path(), QStringLiteral("c%1.png").arg(i), QColor(colors[i])));
      settle(library);
    }
    const QString collage = QUrl(library.detail().value("thumbnail").toString()).toLocalFile();
    QVERIFY2(QFile::exists(collage), "collage should exist once four covers do");
    QCOMPARE(QImage(collage).size(), QSize(640, 640));

    library.setPlaylistCover(id, writePng(art.path(), QStringLiteral("mine.png"), QColor(Qt::white)));
    settle(library);
    QVERIFY(!QFile::exists(collage));
    QVERIFY(library.detail().value("hasCustomCover").toBool());
    library.clearPlaylistCover(id);
    QTRY_VERIFY(QFile::exists(QUrl(library.detail().value("thumbnail").toString()).toLocalFile()));
  }

  void customLyricsBeatEmbeddedOnes() {
    if (!toolAvailable(local::ffprobeProgram()))
      QSKIP("ffprobe is not installed");
    QTemporaryDir music, store;
    LocalLibrary library(store.path());
    library.importFiles({writeWav(music.path(), QStringLiteral("s.wav"))});
    settle(library);
    const QString id = library.songs().first().toMap().value("id").toString();
    QCOMPARE(library.lyricsFor(id).value("status").toString(), QStringLiteral("unavailable"));
    QFile lrc(music.path() + QStringLiteral("/s.lrc"));
    QVERIFY(lrc.open(QIODevice::WriteOnly));
    lrc.write("[00:01.00]hello\n");
    lrc.close();
    library.setTrackLyrics(id, lrc.fileName());
    QCOMPARE(library.lyricsFor(id).value("mode").toString(), QStringLiteral("synced"));
    library.clearTrackLyrics(id);
    QCOMPARE(library.lyricsFor(id).value("status").toString(), QStringLiteral("unavailable"));
  }
};

QTEST_GUILESS_MAIN(LocalLibraryTest)
#include "local_library_test.moc"
