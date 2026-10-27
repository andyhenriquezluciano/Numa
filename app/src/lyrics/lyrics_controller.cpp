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

#include "lyrics_controller.h"
#include "lyrics_identity.h"
#include "local/local_library.h"
#include "local/local_track.h"
#include "offline/download_manager.h"

#include "auth/auth_manager.h"
#include "playback/playback_controller.h"
#include "providers/youtube/youtube_provider.h"

#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>
#include <utility>

namespace {
constexpr qsizetype maxCachedTracks = 24;
}

LyricsController::LyricsController(YouTubeProvider *provider, AuthManager *auth,
                                   PlaybackController *playback,
                                   QObject *parent)
    : QObject(parent), m_provider(provider), m_auth(auth),
      m_playback(playback) {
  if (m_provider) {
    connect(m_provider, &YouTubeProvider::resultReady, this,
            &LyricsController::receive);
    connect(m_provider, &YouTubeProvider::requestFailed, this,
            &LyricsController::fail);
  }
  if (m_playback) {
    // Track metadata and transport changes refresh the lyric source.
    connect(m_playback, &PlaybackController::stateChanged, this,
            &LyricsController::syncTrack);
    connect(m_playback, &PlaybackController::queueChanged, this,
            &LyricsController::prefetchTransition, Qt::QueuedConnection);
  }
  m_requestDelay.setSingleShot(true);
  m_requestDelay.setInterval(60);
  connect(&m_requestDelay, &QTimer::timeout, this, [this] {
    const QVariantMap track = std::exchange(m_pendingTrack, QVariantMap{});
    if (!track.isEmpty() && track.value(QStringLiteral("id")).toString() ==
                                viewTrack().value(QStringLiteral("id")).toString())
      request(track);
  });
}

void LyricsController::setLocalLibrary(LocalLibrary *library) {
  m_local = library;
  if (!library)
    return;
  // Lyrics added or removed mid-song show up without a track change.
  connect(library, &LocalLibrary::changed, this, &LyricsController::refreshLocal);
}

void LyricsController::setDownloadManager(DownloadManager *downloads) {
  m_downloads = downloads;
}

void LyricsController::setActive(bool active) {
  if (m_active == active)
    return;
  m_active = active;
  emit activeChanged();
  syncTrack();
}

void LyricsController::setDiscordLyricsEnabled(bool enabled) {
  if (m_discordLyricsEnabled == enabled)
    return;
  m_discordLyricsEnabled = enabled;
  syncTrack();
}

void LyricsController::setFollowIncoming(bool follow) {
  if (m_followIncoming == follow)
    return;
  m_followIncoming = follow;
  emit followIncomingChanged();
  syncTrack();
}

// The incoming song once the fullscreen view has swapped to it mid-mix.
QVariantMap LyricsController::viewTrack() const {
  if (!m_playback)
    return {};
  const QVariantMap incoming = m_playback->transitionTrack();
  return m_followIncoming && !incoming.isEmpty() ? incoming : m_playback->shownTrack();
}

int LyricsController::indexAt(double time) const {
  if (m_mode != QStringLiteral("synced") || m_status != QStringLiteral("ready"))
    return -1;
  int low = 0;
  int high = m_lines.size() - 1;
  int found = -1;
  while (low <= high) {
    const int mid = (low + high) / 2;
    if (m_lines.at(mid).toMap().value(QStringLiteral("startTime")).toDouble() <= time) {
      found = mid;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  return found;
}

void LyricsController::updateActiveLine() {
  const double pos = m_playback ? m_playback->shownAudiblePosition() : 0.0;
  const int index = indexAt(pos);
  const QString text = index < 0 ? QString() : m_lines.at(index).toMap().value(QStringLiteral("text")).toString();
  if (index == m_activeLineIndex && m_trackId == m_activeLineTrackId && text == m_activeLineText)
    return;
  m_activeLineIndex = index;
  m_activeLineTrackId = m_trackId;
  m_activeLineText = text;
  emit activeLineChanged(m_trackId, text);
}

void LyricsController::reload() {
  const QVariantMap track = viewTrack();
  const QString id = track.value(QStringLiteral("id")).toString();
  if (id.isEmpty())
    return;
  const QString key = lyrics::identity(payload(track).value(QStringLiteral("track")).toObject());
  m_cache.remove(key);
  m_cacheOrder.removeAll(key);
  m_requestDelay.stop();
  m_pendingTrack.clear();
  m_requestTrackId.clear();
  request(track);
}

void LyricsController::syncTrack() {
  prefetchTransition();
  const QVariantMap track = viewTrack();
  const QString id = track.value(QStringLiteral("id")).toString();
  const QString key = lyrics::identity(payload(track).value(QStringLiteral("track")).toObject());
  const QString stateKey = key + (m_downloads && m_downloads->isOffline() ? QStringLiteral("/offline") : QStringLiteral("/online"));
  if (stateKey == m_trackKey && id == m_trackId && (m_status != QStringLiteral("idle") || (!m_active && !m_discordLyricsEnabled))) {
    updateActiveLine();
    return;
  }

  m_trackKey = stateKey;
  if (id.isEmpty()) {
    m_requestDelay.stop();
    m_pendingTrack.clear();
    m_requestTrackId.clear();
    reset(QString(), QStringLiteral("idle"));
    return;
  }

  if (const auto cached = m_cache.constFind(key); cached != m_cache.cend()) {
    m_requestDelay.stop();
    m_pendingTrack.clear();
    apply(id, cached.value());
    return;
  }

  // Stay lazy while hidden so skipping through a queue doesn't hammer lyric mirrors.
  if (!m_active && !m_discordLyricsEnabled) {
    m_requestDelay.stop();
    m_pendingTrack.clear();
    reset(id, QStringLiteral("idle"));
    return;
  }
  reset(id, QStringLiteral("loading"));
  m_pendingTrack = track;
  m_requestDelay.start();
}

// Only one speculative request runs, after the visible song has its lyrics.
void LyricsController::prefetchTransition() {
  if (!m_active || m_prefetchId || !m_playback)
    return;
  QVariantMap track = m_playback->transitionTrack();
  if (track.isEmpty()) {
    if (m_status == QStringLiteral("loading") || !m_playback->playing() || m_playback->position() < 2.0)
      return;
    const QVariantList queue = m_playback->shownQueue();
    if (queue.isEmpty())
      return;
    track = queue.first().toMap();
  }
  const QString id = track.value(QStringLiteral("id")).toString();
  const QString key = lyrics::identity(payload(track).value(QStringLiteral("track")).toObject());
  if (id.isEmpty() || id == m_failedPrefetchTrackId || id == m_requestTrackId
      || id == m_trackId || m_cache.contains(key) || local::isLocalTrackId(id))
    return;
  const QVariantMap saved = offlineLyrics(id, key);
  if (saved.value(QStringLiteral("status")).toString() == QStringLiteral("ready") &&
      !saved.value(QStringLiteral("lines")).toList().isEmpty()) {
    if (!m_downloads || !m_downloads->isOffline()) remember(key, saved);
    return;
  }
  if (m_downloads && m_downloads->isOffline())
    return;
  if (!m_provider)
    return;
  m_prefetchTrackId = id;
  m_prefetchKey = key;
  m_prefetchId = m_provider->invoke(QStringLiteral("lyrics.resolve"), payload(track));
}

// Never leaves the machine: the user's own file, or tags inside the song.
void LyricsController::requestLocal(const QVariantMap &track) {
  const QString id = track.value(QStringLiteral("id")).toString();
  m_requestTrackId.clear();
  m_requestId = 0;
  apply(id, m_local ? m_local->lyricsFor(id) : QVariantMap());
}

// Quietly swaps in new lyrics for the playing local song, only if they changed.
void LyricsController::refreshLocal() {
  const QString id = viewTrack().value(QStringLiteral("id")).toString();
  if (!m_active || !local::isLocalTrackId(id) || id != m_trackId || !m_local)
    return;
  const QVariantMap fresh = m_local->lyricsFor(id);
  if (fresh.value(QStringLiteral("lines")).toList() != m_lines ||
      fresh.value(QStringLiteral("mode")).toString() != m_mode)
    apply(id, fresh);
}

QVariantMap LyricsController::offlineLyrics(const QString &id, const QString &key, bool allowStale) const {
  if (!m_downloads || id.isEmpty())
    return {};
  const QString path = m_downloads->lyricsPathFor(id);
  if (path.isEmpty())
    return {};
  QFile file(path);
  if (!file.open(QIODevice::ReadOnly))
    return {};
  QJsonParseError error;
  const QJsonDocument doc = QJsonDocument::fromJson(file.readAll(), &error);
  if (error.error != QJsonParseError::NoError || !doc.isObject())
    return {};
  if (!allowStale && !m_downloads->isOffline() && !lyrics::current(doc.object(), key))
    return {};
  return doc.object().toVariantMap();
}

void LyricsController::request(const QVariantMap &track) {
  const QString id = track.value(QStringLiteral("id")).toString();
  const QString key = lyrics::identity(payload(track).value(QStringLiteral("track")).toObject());
  m_trackKey = key + (m_downloads && m_downloads->isOffline() ? QStringLiteral("/offline") : QStringLiteral("/online"));
  if (local::isLocalTrackId(id)) {
    requestLocal(track);
    return;
  }

  const QVariantMap saved = offlineLyrics(id, key);
  if (saved.value(QStringLiteral("status")).toString() == QStringLiteral("ready") &&
      !saved.value(QStringLiteral("lines")).toList().isEmpty()) {
    m_requestTrackId.clear();
    m_requestId = 0;
    if (!m_downloads || !m_downloads->isOffline()) remember(key, saved);
    apply(id, saved);
    return;
  }

  if (m_downloads && m_downloads->isOffline()) {
    m_requestTrackId.clear();
    m_requestId = 0;
    reset(id, QStringLiteral("unavailable"));
    return;
  }

  if (id == m_requestTrackId && key == m_requestKey)
    return;

  reset(id, QStringLiteral("loading"));
  m_requestTrackId = id;
  m_requestKey = key;
  // A prefetch already on its way answers for the track it fetched.
  if (id == m_prefetchTrackId && key == m_prefetchKey) {
    m_requestId = std::exchange(m_prefetchId, 0);
    m_prefetchTrackId.clear();
    return;
  }
  if (!m_provider) {
    reset(id, QStringLiteral("unavailable"));
    return;
  }
  m_requestId = m_provider->invoke(QStringLiteral("lyrics.resolve"), payload(track));
}

QJsonObject LyricsController::payload(const QVariantMap &track) const {
  const QString id = track.value(QStringLiteral("id")).toString();
  double seconds = 0;
  if (m_playback && !m_playback->shownLoading() && id == viewTrack().value(QStringLiteral("id")).toString())
    seconds = m_followIncoming && !m_playback->transitionTrack().isEmpty()
      ? m_playback->transitionDuration() : m_playback->shownDuration();
  return QJsonObject{{QStringLiteral("track"), lyrics::lookupTrack(track, seconds)},
    {QStringLiteral("session"), m_auth ? m_auth->sessionObject() : QJsonObject{}}};
}

void LyricsController::remember(const QString &trackId, const QVariantMap &result) {
  m_cache.insert(trackId, result);
  m_cacheOrder.removeAll(trackId);
  m_cacheOrder.append(trackId);
  while (m_cacheOrder.size() > maxCachedTracks)
    m_cache.remove(m_cacheOrder.takeFirst());
}

void LyricsController::receive(quint64 requestId, const QJsonValue &result) {
  if (requestId == 0)
    return;
  if (requestId == m_prefetchId) {
    m_prefetchId = 0;
    const QString id = std::exchange(m_prefetchTrackId, QString());
    const QJsonObject obj = lyrics::validated(result.toObject(), m_prefetchKey);
    const QVariantMap data = obj.toVariantMap();
    if (data.value(QStringLiteral("status")) == QStringLiteral("ready")) remember(m_prefetchKey, data);
    if (m_downloads && m_downloads->isDownloaded(id) &&
        data.value(QStringLiteral("status")).toString() == QStringLiteral("ready")) {
      m_downloads->saveLyricsForTrack(id, obj);
    }
    return;
  }
  if (requestId != m_requestId)
    return;
  m_requestId = 0;
  const QString id = std::exchange(m_requestTrackId, QString());
  const QJsonObject obj = lyrics::validated(result.toObject(), m_requestKey);
  const QVariantMap data = obj.toVariantMap();
  if (data.value(QStringLiteral("status")) == QStringLiteral("ready")) remember(m_requestKey, data);
  if (m_downloads && m_downloads->isDownloaded(id) &&
      data.value(QStringLiteral("status")).toString() == QStringLiteral("ready")) {
    m_downloads->saveLyricsForTrack(id, obj);
  }

  // Playback may have moved on while hidden; the cache keeps it for a return visit.
  if (id == viewTrack().value(QStringLiteral("id")).toString() &&
      m_requestKey == lyrics::identity(payload(viewTrack()).value(QStringLiteral("track")).toObject())) {
    const QVariantMap fallback = offlineLyrics(id, {}, true);
    apply(id, data.value(QStringLiteral("status")) == QStringLiteral("ready") ? data : fallback);
  }
}

void LyricsController::fail(quint64 requestId, const QString &) {
  if (requestId == 0)
    return;
  if (requestId == m_prefetchId) {
    m_prefetchId = 0;
    m_failedPrefetchTrackId = std::exchange(m_prefetchTrackId, QString());
    return;
  }
  if (requestId != m_requestId)
    return;
  m_requestId = 0;
  const QString id = std::exchange(m_requestTrackId, QString());
  if (id == m_trackId) {
    const QVariantMap fallback = offlineLyrics(id, {}, true);
    apply(id, fallback);
  }
}

void LyricsController::apply(const QString &trackId, const QVariantMap &result) {
  const QVariantList lines = result.value(QStringLiteral("lines")).toList();
  m_trackId = trackId;
  m_status = result.value(QStringLiteral("status")).toString() == QStringLiteral("ready") && !lines.isEmpty()
                 ? QStringLiteral("ready")
                 : QStringLiteral("unavailable");
  m_mode = result.value(QStringLiteral("mode")).toString();
  m_source = result.value(QStringLiteral("source")).toString();
  m_lines = lines;
  emit stateChanged();
  updateActiveLine();
}

void LyricsController::reset(const QString &trackId, const QString &status) {
  m_trackId = trackId;
  m_status = status;
  m_mode.clear();
  m_source.clear();
  m_lines.clear();
  emit stateChanged();
  updateActiveLine();
}
