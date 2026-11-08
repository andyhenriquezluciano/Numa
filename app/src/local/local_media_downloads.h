/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
#pragma once

#include <QObject>
#include <QNetworkAccessManager>
#include <QTimer>
#include <QPointer>
#include <QVariantList>
#include <QVariantMap>

class FileFetch;
class LocalLibrary;
class YouTubeProvider;
class AuthManager;
class AnimatedArtworkService;
class LyricsController;
class LyricsTranslator;

// Fetches optional extras for existing audio, without touching the source files.
class LocalMediaDownloads final : public QObject {
  Q_OBJECT
  Q_PROPERTY(bool lyrics READ lyrics WRITE setLyrics NOTIFY optionsChanged)
  Q_PROPERTY(bool translation READ translation WRITE setTranslation NOTIFY optionsChanged)
  Q_PROPERTY(bool artwork READ artwork WRITE setArtwork NOTIFY optionsChanged)
  Q_PROPERTY(bool busy READ busy NOTIFY changed)
  Q_PROPERTY(int completed READ completed NOTIFY changed)
  Q_PROPERTY(int total READ total NOTIFY changed)
  Q_PROPERTY(QString message READ message NOTIFY changed)
public:
  LocalMediaDownloads(LocalLibrary *library, YouTubeProvider *provider, AuthManager *auth,
                      AnimatedArtworkService *artwork, LyricsTranslator *preferences, QObject *parent = nullptr);
  bool lyrics() const { return m_lyrics; }
  bool translation() const { return m_translation; }
  bool artwork() const { return m_artwork; }
  bool busy() const { return m_busy; }
  int completed() const { return m_completed; }
  int total() const { return m_tracks.size(); }
  QString message() const { return m_message; }
  void setLyrics(bool value);
  void setTranslation(bool value);
  void setArtwork(bool value);
  Q_INVOKABLE void start();
  Q_INVOKABLE void cancel();
signals:
  void optionsChanged();
  void changed();
private:
  void saveOptions();
  void next();
  void receiveLyrics(const QVariantMap &data);
  void translate(const QVariantMap &data);
  void fetchArtwork();
  void finishTrack();
  LocalLibrary *m_library;
  YouTubeProvider *m_provider;
  AuthManager *m_auth;
  AnimatedArtworkService *m_artworkService;
  LyricsTranslator *m_preferences;
  LyricsController *m_document;
  LyricsTranslator *m_translator;
  QNetworkAccessManager m_network;
  QTimer m_timeout;
  QPointer<FileFetch> m_fetch;
  QVariantList m_tracks;
  QVariantMap m_track;
  QString m_message;
  quint64 m_request{0};
  quint64 m_generation{0};
  int m_completed{0};
  int m_missing{0};
  int m_lyricsSaved{0}, m_artworkSaved{0}, m_translationsSaved{0};
  bool m_translationReady{false};
  bool m_busy{false};
  bool m_lyrics{false}, m_translation{false}, m_artwork{false};
  bool m_runLyrics{false}, m_runTranslation{false}, m_runArtwork{false};
  enum class Phase { Idle, Lyrics, Translation, Artwork };
  Phase m_phase{Phase::Idle};
};
