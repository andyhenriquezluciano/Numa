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

// Everything that reads a user's files: songs, pictures and lyrics.

#include "local_async.h"
#include "account/cloud_cover_crop.h"
#include "local_jobs.h"
#include "local_library.h"
#include "local_lyrics.h"
#include "local_track.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QSaveFile>
#include <QImageReader>
#include <QUuid>

using namespace local;

bool LocalLibrary::scanningImport() const {
  for (const auto &progress : m_imports)
    if (progress->total.load() < 0)
      return true;
  return false;
}

int LocalLibrary::importCompleted() const {
  int count = 0;
  for (const auto &progress : m_imports)
    count += progress->completed.load();
  return count;
}

int LocalLibrary::importTotal() const {
  int count = 0;
  for (const auto &progress : m_imports)
    count += qMax(0, progress->total.load());
  return count;
}

void LocalLibrary::importFiles(const QVariantList &files, const QString &playlistId) {
  QStringList paths;
  for (const QVariant &file : files) {
    const QString path = pathFrom(file);
    if (!path.isEmpty())
      paths.append(path);
  }
  if (paths.isEmpty())
    return;
  const QSet<QString> known(m_store.tracks.keyBegin(), m_store.tracks.keyEnd());
  const QString covers = m_store.coversDir();
  QDir().mkpath(covers);
  beginJob();
  const auto progress = std::make_shared<ImportProgress>();
  m_imports.append(progress);
  emit importProgressChanged();
  // Poll thread-safe counters instead of posting one GUI event per file.
  auto *progressTimer = new QTimer(this);
  progressTimer->setInterval(80);
  connect(progressTimer, &QTimer::timeout, this, &LocalLibrary::importProgressChanged);
  progressTimer->start();
  // The pool does the ffprobe waiting so the window keeps its manners.
  runAsync<ImportResult>(
      this, [paths, known, covers, progress] {
        return importPaths(paths, known, covers, [progress](int completed, int total) {
          progress->total.store(total);
          progress->completed.store(completed);
        });
      },
      [this, playlistId, progress, progressTimer](ImportResult result) {
        progressTimer->stop();
        progressTimer->deleteLater();
        emit importProgressChanged();
        finishImport(result, playlistId);
        m_imports.removeOne(progress);
        emit importProgressChanged();
        endJob();
      });
}

void LocalLibrary::finishImport(const ImportResult &result, const QString &playlistId) {
  for (const TrackRecord &record : result.records) {
    if (m_store.tracks.contains(record.id))
      continue;
    m_store.tracks.insert(record.id, record);
    // Newest first, but a folder keeps its own order within the batch.
    m_store.trackOrder.prepend(record.id);
  }
  PlaylistRecord *playlist = m_store.playlist(playlistId);
  int added = 0;
  if (playlist) {
    for (const QString &id : result.ids) {
      if (!playlist->trackIds.contains(id)) {
        playlist->trackIds.append(id);
        ++added;
      }
    }
  }
  commit(playlistId);
  if (result.ids.isEmpty()) {
    emit noticeRequested(tr("No audio files found."));
  } else if (playlist) {
    emit noticeRequested(tr("Added %n song(s) to “%1”.", nullptr, added).arg(playlist->title));
  } else {
    emit noticeRequested(tr("Added %n song(s) to your library.", nullptr, static_cast<int>(result.records.size())));
  }
}

void LocalLibrary::setPlaylistCover(const QString &playlistId, const QVariant &file) {
  const QString source = pathFrom(file);
  if (!m_store.playlist(playlistId) || source.isEmpty())
    return;
  const QString base = QStringLiteral("pl-") + playlistId.mid(kPlaylistPrefix.size());
  const QString covers = m_store.coversDir();
  const QString animated = m_store.animatedDir();
  beginJob();
  runAsync<CoverResult>(
      this,
      [source, base, covers, animated] {
        CoverResult result = ingestCover(source, base, covers, animated);
        return result;
      },
      [this, playlistId](CoverResult result) {
        applyCover(playlistId, true, result);
        endJob();
      });
}

bool LocalLibrary::setPlaylistCoverCrop(const QString &playlistId, const QVariant &file, const QRectF &crop) {
  if (!m_store.playlist(playlistId)) return false;
  const QString source = pathFrom(file);
  const QFileInfo input(source);
  if (!input.isFile() || input.size() > 16 * 1024 * 1024) return false;
  QImageReader reader(source);
  const QSize size = reader.size();
  if (!size.isValid() || qint64(size.width()) * size.height() > 16000000) return false;
  reader.setAutoTransform(true);
  const QImage image = cloud::cropCover(reader.read(), crop);
  if (image.isNull()) return false;
  QDir().mkpath(m_store.coversDir());
  const QString target = m_store.coversDir() + QStringLiteral("/pl-") + playlistId.mid(kPlaylistPrefix.size())
      + QLatin1Char('-') + QUuid::createUuid().toString(QUuid::WithoutBraces) + QStringLiteral(".png");
  QSaveFile output(target);
  if (!output.open(QIODevice::WriteOnly) || !image.save(&output, "PNG") || !output.commit()) return false;
  CoverResult result;
  result.ok = true;
  result.still = target;
  applyCover(playlistId, true, result);
  return true;
}

void LocalLibrary::setTrackCover(const QString &trackId, const QVariant &file) {
  const QString source = pathFrom(file);
  if (!m_store.tracks.contains(trackId) || source.isEmpty())
    return;
  const QString base = QStringLiteral("tr-") + trackId.mid(kTrackPrefix.size());
  const QString covers = m_store.coversDir();
  const QString animated = m_store.animatedDir();
  beginJob();
  runAsync<CoverResult>(
      this, [source, base, covers, animated] { return ingestCover(source, base, covers, animated); },
      [this, trackId](CoverResult result) {
        applyCover(trackId, false, result);
        endJob();
      });
}

// Runs on the GUI thread once a picture has been copied into the store.
void LocalLibrary::applyCover(const QString &ownerId, bool playlist, const CoverResult &result) {
  if (!result.ok) {
    emit noticeRequested(result.error.isEmpty() ? tr("That picture could not be used.") : result.error);
    return;
  }
  if (playlist) {
    PlaylistRecord *record = m_store.playlist(ownerId);
    if (!record) {
      dropCoverFiles(result.still, result.animated);
      return;
    }
    dropCoverFiles(record->coverPath, record->animatedPath);
    record->automaticCover = false;
    record->automaticTrackId.clear();
    record->coverPath = result.still;
    record->animatedPath = result.animated;
    commit(ownerId);
    return;
  }
  const auto it = m_store.tracks.find(ownerId);
  if (it == m_store.tracks.end()) {
    dropCoverFiles(result.still, result.animated);
    return;
  }
  // Only the user's own earlier pick is ours to delete; embedded art is shared.
  if (it->customCover)
    dropCoverFiles(it->coverPath, it->animatedPath);
  it->coverPath = result.still;
  it->animatedPath = result.animated;
  it->customCover = true;
  commit();
}

void LocalLibrary::clearPlaylistCover(const QString &playlistId) {
  PlaylistRecord *playlist = m_store.playlist(playlistId);
  if (!playlist || (playlist->coverPath.isEmpty() && playlist->animatedPath.isEmpty() && !playlist->automaticCover))
    return;
  playlist->automaticCover = false;
  playlist->automaticTrackId.clear();
  dropCoverFiles(playlist->coverPath, playlist->animatedPath);
  playlist->coverPath.clear();
  playlist->animatedPath.clear();
  commit(playlistId);
}

// Back to whatever the file's own tags carried, found again by the next import.
void LocalLibrary::clearTrackCover(const QString &trackId) {
  const auto it = m_store.tracks.find(trackId);
  if (it == m_store.tracks.end() || !it->customCover)
    return;
  dropCoverFiles(it->coverPath, it->animatedPath);
  it->coverPath.clear();
  it->animatedPath.clear();
  it->customCover = false;
  const QString id = trackId;
  const QString path = it->path;
  const QString covers = m_store.coversDir();
  beginJob();
  runAsync<ImportResult>(
      this,
      [path, covers] { return importPaths({path}, {}, covers); },
      [this, id](ImportResult result) {
        const auto found = m_store.tracks.find(id);
        if (found != m_store.tracks.end() && !result.records.isEmpty())
          found->coverPath = result.records.first().coverPath;
        commit();
        endJob();
      });
}

void LocalLibrary::setTrackLyrics(const QString &trackId, const QVariant &file) {
  const auto it = m_store.tracks.find(trackId);
  const QString source = pathFrom(file);
  if (it == m_store.tracks.end() || source.isEmpty())
    return;
  if (parseLyricsFile(source).value(QStringLiteral("status")).toString() != QStringLiteral("ready")) {
    emit noticeRequested(tr("That file does not look like lyrics. Try an .lrc, .srt or .txt file."));
    return;
  }
  QDir().mkpath(m_store.lyricsDir());
  const QString target = m_store.lyricsDir() + QLatin1Char('/') + trackId.mid(kTrackPrefix.size()) + QLatin1Char('.') +
                         QFileInfo(source).suffix().toLower();
  if (!it->lyricsPath.isEmpty())
    QFile::remove(it->lyricsPath);
  QFile::remove(target);
  if (!QFile::copy(source, target)) {
    it->lyricsPath.clear();
    emit noticeRequested(tr("The lyrics could not be copied into the library."));
    commit();
    return;
  }
  it->lyricsPath = target;
  commit();
  emit noticeRequested(tr("Lyrics saved for “%1”.").arg(it->title));
}

void LocalLibrary::clearTrackLyrics(const QString &trackId) {
  const auto it = m_store.tracks.find(trackId);
  if (it == m_store.tracks.end() || it->lyricsPath.isEmpty())
    return;
  QFile::remove(it->lyricsPath);
  it->lyricsPath.clear();
  commit();
}

QVariantMap LocalLibrary::lyricsFor(const QString &trackId) const {
  const auto it = m_store.tracks.constFind(trackId);
  if (it == m_store.tracks.cend())
    return parseLyrics({});
  if (!it->lyricsPath.isEmpty()) {
    QVariantMap custom;
    if (it->lyricsPath.endsWith(QStringLiteral(".json"))) {
      QFile file(it->lyricsPath);
      if (file.open(QIODevice::ReadOnly)) custom = QJsonDocument::fromJson(file.readAll()).object().toVariantMap();
    } else {
      custom = parseLyricsFile(it->lyricsPath);
    }
    if (custom.value(QStringLiteral("status")).toString() == QStringLiteral("ready"))
      return custom;
  }
  return parseLyrics(it->embeddedLyrics);
}

bool LocalLibrary::saveDownloadedLyrics(const QString &id, const QVariantMap &data) {
  auto it = m_store.tracks.find(id);
  if (it == m_store.tracks.end() || data.value(QStringLiteral("status")) != QStringLiteral("ready")
      || data.value(QStringLiteral("lines")).toList().isEmpty()) return false;
  // Keep user supplied and embedded lyrics ahead of automatic downloads.
  if (lyricsFor(id).value(QStringLiteral("status")) == QStringLiteral("ready")) return true;
  QDir().mkpath(m_store.lyricsDir());
  const QString path = m_store.lyricsDir() + QLatin1Char('/') + id.mid(kTrackPrefix.size()) + QStringLiteral(".json");
  QSaveFile file(path);
  if (!file.open(QIODevice::WriteOnly)) return false;
  const auto bytes = QJsonDocument::fromVariant(data).toJson();
  if (file.write(bytes) != bytes.size() || !file.commit()) return false;
  it->lyricsPath = path;
  commit();
  return true;
}

QString LocalLibrary::downloadedArtworkPath(const QString &id) const {
  if (!m_store.tracks.contains(id)) return {};
  QDir().mkpath(m_store.animatedDir());
  return m_store.animatedDir() + QLatin1Char('/') + id.mid(kTrackPrefix.size()) + QStringLiteral(".mp4");
}

bool LocalLibrary::saveDownloadedArtwork(const QString &id, const QString &path) {
  auto it = m_store.tracks.find(id);
  if (it == m_store.tracks.end() || path != downloadedArtworkPath(id) || !QFileInfo::exists(path)) return false;
  if (!it->animatedPath.isEmpty()) return true;
  it->animatedPath = path;
  commit();
  return true;
}

void LocalLibrary::setAutomaticCover(const QString &id, bool enabled, const QVariantMap &track) {
  auto *playlist = m_store.playlist(id);
  if (!playlist) return;
  playlist->automaticCover = enabled;
  if (!enabled) playlist->automaticTrackId.clear();
  commit(id);
  if (enabled) updateAutomaticCover(track);
}

void LocalLibrary::updateAutomaticCover(const QVariantMap &track) {
  QString origin = track.value(QStringLiteral("queueOrigin")).toMap().value(QStringLiteral("id")).toString();
  if (origin.isEmpty()) origin = track.value(QStringLiteral("localPlaylistId")).toString();
  if (origin.startsWith(QStringLiteral("VL"))) origin = origin.mid(2);
  auto *playlist = m_store.playlist(origin);
  const QString id = track.value(QStringLiteral("id")).toString();
  if (!playlist || !playlist->automaticCover || !playlist->trackIds.contains(id) || playlist->automaticTrackId == id) return;
  playlist->automaticTrackId = id;
  commit(origin);
}
