/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
#include "local_media_downloads.h"
#include "local_library.h"
#include "appearance/animated_artwork_service.h"
#include "auth/auth_manager.h"
#include "lyrics/lyrics_controller.h"
#include "lyrics/lyrics_identity.h"
#include "lyrics/translation/lyrics_translator.h"
#include "offline/file_fetch.h"
#include "providers/youtube/youtube_provider.h"
#include <QFile>
#include <QFileInfo>
#include <QPointer>
#include <QSettings>
#include <QUrl>

LocalMediaDownloads::LocalMediaDownloads(LocalLibrary *library, YouTubeProvider *provider, AuthManager *auth,
    AnimatedArtworkService *artwork, LyricsTranslator *preferences, QObject *parent)
    : QObject(parent), m_library(library), m_provider(provider), m_auth(auth), m_artworkService(artwork),
      m_preferences(preferences), m_document(new LyricsController(nullptr, nullptr, nullptr, this)),
      m_translator(new LyricsTranslator(m_document, this, false)) {
  QSettings settings;
  m_lyrics = settings.value(QStringLiteral("downloads/localLyrics"), false).toBool();
  m_translation = m_lyrics && settings.value(QStringLiteral("downloads/localTranslation"), false).toBool();
  m_artwork = settings.value(QStringLiteral("downloads/localArtwork"), false).toBool();
  m_timeout.setSingleShot(true);
  connect(&m_timeout, &QTimer::timeout, this, [this] {
    if (!m_busy) return;
    ++m_missing;
    m_request = 0;
    if (m_phase == Phase::Artwork) finishTrack();
    else fetchArtwork();
  });
  if (m_provider) {
    connect(m_provider, &YouTubeProvider::resultReady, this, [this](quint64 request, const QJsonValue &value) {
      if (!m_busy || m_phase != Phase::Lyrics || !m_request || request != m_request) return;
      m_request = 0;
      receiveLyrics(value.toObject().toVariantMap());
    });
    connect(m_provider, &YouTubeProvider::requestFailed, this, [this](quint64 request, const QString &) {
      if (!m_busy || !m_request || request != m_request) return;
      m_request = 0;
      ++m_missing;
      fetchArtwork();
    });
  }
  connect(m_translator, &LyricsTranslator::stateChanged, this, [this] {
    if (!m_busy || m_phase != Phase::Translation) return;
    const QString status = m_translator->status();
    if (status == QStringLiteral("ready")) { m_translationReady = true; fetchArtwork(); }
    else if (status == QStringLiteral("failed") || status == QStringLiteral("unsupported")) {
      ++m_missing;
      fetchArtwork();
    }
  });
}

void LocalMediaDownloads::saveOptions() {
  QSettings settings;
  settings.setValue(QStringLiteral("downloads/localLyrics"), m_lyrics);
  settings.setValue(QStringLiteral("downloads/localTranslation"), m_translation);
  settings.setValue(QStringLiteral("downloads/localArtwork"), m_artwork);
  emit optionsChanged();
}
void LocalMediaDownloads::setLyrics(bool value) {
  if (m_busy) return;
  m_lyrics = value;
  if (!value) m_translation = false;
  saveOptions();
}
void LocalMediaDownloads::setTranslation(bool value) {
  if (m_busy) return;
  m_translation = value;
  if (value) m_lyrics = true;
  saveOptions();
}
void LocalMediaDownloads::setArtwork(bool value) {
  if (m_busy) return;
  m_artwork = value;
  saveOptions();
}
void LocalMediaDownloads::start() {
  if (m_busy || (!m_lyrics && !m_artwork)) return;
  m_tracks = m_library->songs();
  m_completed = m_missing = 0;
  m_lyricsSaved = m_artworkSaved = m_translationsSaved = 0;
  m_runLyrics = m_lyrics;
  m_runTranslation = m_lyrics && m_translation;
  m_runArtwork = m_artwork;
  m_busy = true;
  next();
}
void LocalMediaDownloads::cancel() {
  ++m_generation;
  m_busy = false;
  m_request = 0;
  m_phase = Phase::Idle;
  m_timeout.stop();
  if (m_fetch) m_fetch->abort();
  m_translator->setEnabled(false);
  m_message = tr("Download stopped. Saved extras are kept.");
  emit changed();
}
void LocalMediaDownloads::next() {
  if (!m_busy) return;
  if (m_completed >= m_tracks.size()) {
    m_busy = false;
    m_phase = Phase::Idle;
    QStringList results;
    if (m_runLyrics) results.append(tr("Downloaded lyrics: %1 of %2 songs.").arg(m_lyricsSaved).arg(total()));
    if (m_runTranslation) results.append(tr("Saved translations: %1 of %2 songs.").arg(m_translationsSaved).arg(total()));
    if (m_runArtwork) results.append(tr("Downloaded animated artwork: %1 of %2 songs.").arg(m_artworkSaved).arg(total()));
    m_message = results.join(QLatin1Char('\n'));
    emit changed();
    return;
  }
  ++m_generation;
  m_translationReady = false;
  m_track = m_tracks.at(m_completed).toMap();
  const QString id = m_track.value(QStringLiteral("id")).toString();
  m_message = tr("Processing %1").arg(m_track.value(QStringLiteral("title")).toString());
  emit changed();
  if (!m_runLyrics || m_library->track(id).isEmpty()) { fetchArtwork(); return; }
  const auto saved = m_library->lyricsFor(id);
  if (saved.value(QStringLiteral("status")) == QStringLiteral("ready")) { translate(saved); return; }
  if (!m_provider) { ++m_missing; fetchArtwork(); return; }
  m_phase = Phase::Lyrics;
  m_timeout.start(60000);
  m_request = m_provider->invoke(QStringLiteral("lyrics.resolve"),
      QJsonObject{{QStringLiteral("track"), lyrics::lookupTrack(m_track)},
                  {QStringLiteral("session"), m_auth ? m_auth->sessionObject() : QJsonObject{}}});
}
void LocalMediaDownloads::receiveLyrics(const QVariantMap &data) {
  m_timeout.stop();
  const QString id = m_track.value(QStringLiteral("id")).toString();
  if (!m_library->saveDownloadedLyrics(id, data)) { ++m_missing; fetchArtwork(); return; }
  translate(m_library->lyricsFor(id));
}
void LocalMediaDownloads::translate(const QVariantMap &data) {
  if (!m_runTranslation) { fetchArtwork(); return; }
  m_phase = Phase::Translation;
  m_timeout.start(300000);
  m_translator->setEnabled(false);
  m_translator->setTargetLanguage(m_preferences->targetLanguage());
  m_translator->setProvider(m_preferences->provider());
  m_translator->setQuality(m_preferences->quality());
  m_document->setDocument(m_track.value(QStringLiteral("id")).toString(), data);
  m_translator->setEnabled(true);
}
void LocalMediaDownloads::fetchArtwork() {
  m_timeout.stop();
  m_phase = Phase::Artwork;
  m_translator->setEnabled(false);
  const QString id = m_track.value(QStringLiteral("id")).toString();
  const auto current = m_library->track(id);
  if (!m_runArtwork || current.isEmpty() || !current.value(QStringLiteral("localAnimatedCover")).toString().isEmpty()) {
    finishTrack(); return;
  }
  if (!m_artworkService) { ++m_missing; finishTrack(); return; }
  const auto generation = m_generation;
  m_timeout.start(90000);
  QPointer<LocalMediaDownloads> guard(this);
  m_artworkService->resolveTrackArtwork(m_track.value(QStringLiteral("title")).toString(),
      m_track.value(QStringLiteral("artist")).toString(), m_track.value(QStringLiteral("album")).toString(), generation,
      [guard, generation, id](quint64, const QString &url) {
    if (!guard || !guard->m_busy || guard->m_generation != generation || guard->m_phase != Phase::Artwork) return;
    const QString path = guard->m_library->downloadedArtworkPath(id);
    if (url.isEmpty() || path.isEmpty()) { ++guard->m_missing; guard->finishTrack(); return; }
    auto *fetch = new FileFetch(&guard->m_network, QUrl(url), path, 150LL * 1024 * 1024, 90000, guard);
    guard->m_fetch = fetch;
    connect(fetch, &FileFetch::finished, guard, [guard, generation, id, path, fetch](bool ok) {
      fetch->deleteLater();
      if (!guard || !guard->m_busy || guard->m_generation != generation) return;
      if (!ok || !guard->m_library->saveDownloadedArtwork(id, path)) {
        QFile::remove(path);
        ++guard->m_missing;
      }
      guard->finishTrack();
    });
    guard->m_timeout.stop(); // FileFetch owns its transfer timeout.
    fetch->start();
  }, true);
}
void LocalMediaDownloads::finishTrack() {
  m_timeout.stop();
  const auto saved = m_library->track(m_track.value(QStringLiteral("id")).toString());
  if (saved.value(QStringLiteral("hasDownloadedLyrics")).toBool()) ++m_lyricsSaved;
  if (saved.value(QStringLiteral("hasDownloadedArtwork")).toBool()) ++m_artworkSaved;
  if (m_translationReady && !saved.isEmpty()) ++m_translationsSaved;
  m_phase = Phase::Idle;
  ++m_completed;
  emit changed();
  QTimer::singleShot(0, this, &LocalMediaDownloads::next);
}
