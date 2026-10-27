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

#include "download_manager.h"
#include "lyrics/lyrics_identity.h"

#include "auth/auth_manager.h"
#include "providers/youtube/youtube_provider.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QSaveFile>

void DownloadManager::receiveResult(quint64 request, const QJsonValue &result) {
  if (m_lyricsRequests.contains(request))
    receiveLyrics(request, result);
  else
    receiveStream(request, result);
}

void DownloadManager::fetchLyrics(const QString &id) {
  auto it = m_jobs.find(id);
  if (it == m_jobs.end())
    return;
  if (!m_provider || !m_auth || it->track.value(QStringLiteral("title")).toString().trimmed().isEmpty()) {
    finishLyrics(id);
    return;
  }
  const QJsonObject payload = lyrics::lookupTrack(m_store.tracks.value(id).meta.toVariantMap());

  const quint64 request = m_provider->invoke(
      QStringLiteral("lyrics.resolve"),
      QJsonObject{{QStringLiteral("track"), payload},
                  {QStringLiteral("session"), m_auth->sessionObject()}});
  it->lyricsRequest = request;
  m_lyricsRequests.insert(request, id);
}

void DownloadManager::receiveLyrics(quint64 request, const QJsonValue &result) {
  const QString id = m_lyricsRequests.take(request);
  if (id.isEmpty())
    return;
  auto it = m_jobs.find(id);
  if (it != m_jobs.end() && it->lyricsRequest == request)
    it->lyricsRequest = 0;

  const QJsonObject data = lyrics::validated(result.toObject(), lyrics::identity(lyrics::lookupTrack(m_store.tracks.value(id).meta.toVariantMap())));
  const QString status = data.value(QStringLiteral("status")).toString();
  const QJsonArray lines = data.value(QStringLiteral("lines")).toArray();
  if (status == QLatin1String("ready") && !lines.isEmpty()) {
    saveLyricsFile(id, data);
  }
  finishLyrics(id);
}

void DownloadManager::failLyrics(quint64 request, const QString &) {
  const QString id = m_lyricsRequests.take(request);
  if (id.isEmpty())
    return;
  auto it = m_jobs.find(id);
  if (it != m_jobs.end() && it->lyricsRequest == request)
    it->lyricsRequest = 0;
  finishLyrics(id);
}

void DownloadManager::finishLyrics(const QString &id) {
  auto it = m_jobs.find(id);
  if (it == m_jobs.end())
    return;
  it->lyricsDone = true;
  checkComplete(id);
}

void DownloadManager::finishArtwork(const QString &id) {
  auto it = m_jobs.find(id);
  if (it == m_jobs.end())
    return;
  it->artworkDone = true;
  checkComplete(id);
}

void DownloadManager::checkComplete(const QString &id) {
  auto it = m_jobs.find(id);
  if (it == m_jobs.end())
    return;
  if (it->artworkDone && it->lyricsDone)
    complete(id);
}

bool DownloadManager::saveLyricsFile(const QString &id, const QJsonObject &data) {
  if (id.isEmpty() || !m_store.tracks.contains(id))
    return false;
  QDir().mkpath(m_store.lyricsDir());
  const QString path = m_store.lyricsDir() + QLatin1Char('/') + offline::OfflineStore::stem(id) + QStringLiteral(".json");
  QSaveFile file(path);
  if (!file.open(QIODevice::WriteOnly))
    return false;
  file.write(QJsonDocument(data).toJson(QJsonDocument::Indented));
  if (!file.commit())
    return false;
  auto it = m_store.tracks.find(id);
  if (it != m_store.tracks.end()) {
    it->lyricsPath = path;
    touch();
  }
  return true;
}

bool DownloadManager::saveLyricsForTrack(const QString &id, const QJsonObject &lyrics) {
  return saveLyricsFile(id, lyrics);
}

bool DownloadManager::saveLyricsForTrack(const QString &id, const QVariantMap &lyrics) {
  return saveLyricsFile(id, QJsonObject::fromVariantMap(lyrics));
}

QString DownloadManager::lyricsPathFor(const QString &id) const {
  const auto it = m_store.tracks.constFind(id);
  return it != m_store.tracks.cend() && QFileInfo::exists(it->lyricsPath) ? it->lyricsPath : QString();
}
