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

#include "account/numa_account.h"
#include "account/cloud_library.h"
#include "auth/auth_manager.h"
#include "auth/auth_diagnostics.h"
#include "album/album_controller.h"
#include "playlist/playlist_controller.h"
#include "backend_info.h"
#include "default_preferences.h"
#include "social/listening_party.h"
#include "crash_handler.h"
#include "docs/docs_assistant.h"
#include "docs/docs_embedder.h"
#include "docs/docs_library.h"
#include "appearance/appearance_settings.h"
#include "appearance/animated_artwork_service.h"
#include "appearance/artwork_video_cache.h"
#include "heap_trim.h"
#include "home/home_controller.h"
#include "integrations/integrations.h"
#include "integrations/spotify_canvas.h"
#include "instance_coordinator.h"
#include "library/library_actions.h"
#include "local/local_library.h"
#include "lyrics/lyrics_controller.h"
#include "lyrics/translation/lyrics_translator.h"
#include "offline/connectivity_monitor.h"
#include "offline/download_manager.h"
#include "offline/offline_library.h"
#include "playback/artist_video_backdrop.h"
#include "playback/music_video_controller.h"
#include "playback/playback_controller.h"
#include "providers/qobuz/qobuz_service.h"
#include "providers/youtube/catalog/youtube_catalog.h"
#include "providers/youtube/youtube_provider.h"
#include "screen_wake_lock.h"
#include "search/search_controller.h"
#include "support/support_center.h"
#include "system_media_bridge.h"
#include "system_tray.h"
#include "update/bootstrapper_client.h"
#include "window_state.h"
#include "ui_language.h"

// why does QT flood code bases with Q instead of a regular class name?
// I guess it's easier to type but whatever.
#include <QApplication>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QFont>
#include <QFontDatabase>
#include <QIcon>
#include <QJsonArray>
#include <QJsonDocument>
#include <QMessageBox>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickWindow>
#include <QSGRendererInterface>
#include <QQuickStyle>
#include <QSurfaceFormat>
#include <QTextStream>
#include <QTimer>
#include <QUrl>
#include <QVulkanInstance>
#include <chrono>
#include <cstdlib>
#include <memory>
#include <thread>

#ifdef Q_OS_WIN
#include <shobjidl.h>
#endif

#if defined(__GLIBC__)
#include <malloc.h>
#endif

#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QQmlNetworkAccessManagerFactory>

class NoHttp2NetworkAccessManager : public QNetworkAccessManager {
public:
  explicit NoHttp2NetworkAccessManager(QObject *parent = nullptr)
      : QNetworkAccessManager(parent) {}

protected:
  QNetworkReply *createRequest(Operation op, const QNetworkRequest &request,
                               QIODevice *outgoingData = nullptr) override {
    QNetworkRequest req(request);
    req.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
    return QNetworkAccessManager::createRequest(op, req, outgoingData);
  }
};

class NoHttp2NetworkFactory : public QQmlNetworkAccessManagerFactory {
public:
  QNetworkAccessManager *create(QObject *parent) override {
    return new NoHttp2NetworkAccessManager(parent);
  }
};

// Keep release history in the build so About works even when the update server does not.
static QVariantList bundledReleaseNotes() {
  QFile file(QStringLiteral(":/release-notes/releases.json"));
  if (!file.open(QIODevice::ReadOnly)) {
    qWarning("Could not open bundled release notes");
    return {};
  }
  const QJsonDocument document = QJsonDocument::fromJson(file.readAll());
  if (!document.isArray()) {
    qWarning("Bundled release notes are not a JSON array");
    return {};
  }
  return document.array().toVariantList();
}

// Bounds ~QGuiApplication. Qt's QHostInfo pool joins in-flight getaddrinfo()
// calls when the application object dies, which blocks for the full resolver
// timeout on a bad network. DNS: the only lookup that ghosts you on the way out.
struct ShutdownDeadline {
  int exitCode = 0;
  ~ShutdownDeadline() {
    std::thread([code = exitCode] {
      std::this_thread::sleep_for(std::chrono::seconds(2));
      std::_Exit(code);
    }).detach();
  }
};

int main(int argc, char *argv[]) {
  installCrashHandler();

#ifdef Q_OS_WIN
  // Tell Windows who owns the media flyout before Qt creates any windows.
  // The Start Menu shortcut must use this same ID; otherwise Windows plays shy.
  SetCurrentProcessExplicitAppUserModelID(L"dev.sfg.numa");
#endif

#if defined(__GLIBC__)
  // Qt, FFmpeg and the graphics stack create many worker threads. Glibc's
  // default per-thread arena policy keeps tens of MiB of otherwise idle heap
  // resident in a long-lived GUI process.
  mallopt(M_ARENA_MAX, 2);
#endif

  QCoreApplication::setOrganizationDomain(QStringLiteral("sfg.dev"));
  QCoreApplication::setOrganizationName(QStringLiteral("SFG545"));
  QCoreApplication::setApplicationName(QStringLiteral("Numa"));

  // Enable alpha buffer so frameless windows composite cleanly with per-pixel alpha.
  QQuickWindow::setDefaultAlphaBuffer(true);

  // QApplication for QSystemTrayIcon, whose menus are still widgets in 2026.
  QApplication application(argc, argv);
  QGuiApplication::setApplicationDisplayName(QStringLiteral("NÜMA"));
  // The running binary is the source of truth; About should not time travel to 0.2.0.
  QCoreApplication::setApplicationVersion(QStringLiteral(NUMA_VERSION));
  // Declared before every other local so it arms only once they are all destroyed.
  ShutdownDeadline shutdownDeadline;
  InstanceCoordinator instance;
  const auto instanceResult = instance.start();
  if (instanceResult == InstanceCoordinator::StartResult::Forwarded)
    return 0;
  if (instanceResult == InstanceCoordinator::StartResult::Failed) {
    QMessageBox::critical(nullptr, QStringLiteral("NÜMA"),
                          QStringLiteral("NÜMA could not prepare its startup channel. Please try again."));
    shutdownDeadline.exitCode = 1;
    return 1;
  }

#ifdef Q_OS_WIN
  // Analysis workers inherit this; Windows has no ffmpeg on PATH to fall back to.
  // 165 MB of ffmpeg.exe, and we use it to read audio. Very reasonable.
  const QString bundledFfmpeg =
      QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("ffmpeg.exe"));
  if (!qEnvironmentVariableIsSet("NUMA_FFMPEG") && QFileInfo::exists(bundledFfmpeg))
    qputenv("NUMA_FFMPEG", QDir::toNativeSeparators(bundledFfmpeg).toUtf8());
#endif

#if defined(Q_OS_LINUX) && !defined(Q_OS_ANDROID)
  if (!qEnvironmentVariableIsSet("QSG_RHI_BACKEND") &&
      !qEnvironmentVariableIsSet("QT_QUICK_BACKEND")) {
    QVulkanInstance vulkanProbe;
    if (vulkanProbe.create())
      QQuickWindow::setGraphicsApi(QSGRendererInterface::Vulkan);
  }
#endif
  QSettings initialPreferences;
  initializeDefaultPreferences(initialPreferences);
  startHeapTrim(&application);
  authDiagnostic(QStringLiteral("startup diagnostic-build=3 built=" __DATE__ " " __TIME__));
  QObject::connect(&application, &QCoreApplication::aboutToQuit, &application, [] {
    authDiagnostic(QStringLiteral("application-quitting"));
  });
  application.setWindowIcon(QIcon(QStringLiteral(":/qt/qml/Numa/app/qml/assets/numa.ico")));
  // Custom control backgrounds, content items, and handles need a non-native style.
  QQuickStyle::setStyle(QStringLiteral("Basic"));

  const QStringList fontFiles = {
      QStringLiteral(":/qt/qml/Numa/app/qml/assets/fonts/inter_regular.ttf"),
      QStringLiteral(":/qt/qml/Numa/app/qml/assets/fonts/inter_medium.ttf"),
      QStringLiteral(
          ":/qt/qml/Numa/app/qml/assets/fonts/inter_semibold.ttf"),
      QStringLiteral(":/qt/qml/Numa/app/qml/assets/fonts/inter_bold.ttf"),
  };
  for (const auto &fontFile : fontFiles) {
    QFontDatabase::addApplicationFont(fontFile);
  }

  QFont defaultFont(QStringLiteral("Inter"));
  defaultFont.setStyleHint(QFont::SansSerif);
  application.setFont(defaultFont);
  AppearanceSettings appearance;
  UiLanguage uiLanguage;
  // Spotify lends its looping videos when the cover mirrors come up empty.
  SpotifyCanvas spotifyCanvas;
  AnimatedArtworkService animatedArtwork(&appearance);
  animatedArtwork.setSpotifyCanvas(&spotifyCanvas);
  ArtworkVideoCache artworkVideos;
  BackendInfo backend;
  // The manual nobody reads, with a copy button for the robots that will.
  DocsLibrary docs;
  // The manual answers back, which is more than most manuals manage.
  DocsEmbedder docsEmbedder(DocsEmbedder::defaultProgram(), DocsEmbedder::defaultModels());
  DocsAssistant docsAssistant(&docs, &docsEmbedder, BertTokenizer());
  SystemMediaBridge systemMedia;
  YouTubeProvider youtubeProvider;
  AuthManager authManager(&youtubeProvider);
  NumaAccount numaAccount;
  // Bug reports with receipts: GitHub tells us who answered, we tell you.
  SupportCenter support(&numaAccount);
  YouTubeCatalog youtubeCatalog(&youtubeProvider);
  // YouTube picks the songs; Qobuz, when invited, brings the good speakers.
  QobuzService qobuz;
  SearchController searchController(&youtubeCatalog, &authManager);
  auto homeController = std::make_unique<HomeController>(&youtubeCatalog, &authManager);
  auto artistController = std::make_unique<AlbumController>(&youtubeCatalog, &authManager, nullptr, true);
  auto albumController = std::make_unique<AlbumController>(&youtubeCatalog, &authManager, nullptr, false, &animatedArtwork, &qobuz);
  // Songs and playlists that live on disk instead of YouTube Music: no buffering, no ads, no excuses.
  LocalLibrary localLibrary;
  // Declared before the controllers that read them, so they outlive those controllers.
  ConnectivityMonitor connectivity;
  DownloadManager downloads(&youtubeProvider, &authManager);
  downloads.setConnectivity(&connectivity);
  downloads.setAnimatedArtworkService(&animatedArtwork);
  OfflineLibrary offlineLibrary(&downloads, &localLibrary, &connectivity);
  // A failed request is a hint that the network died; the monitor verifies it.
  QObject::connect(&youtubeProvider, &YouTubeProvider::requestFailed, &connectivity,
                   [&connectivity] { connectivity.reportFailure(); });
  QObject::connect(&connectivity, &ConnectivityMonitor::restored, &authManager,
                   &AuthManager::requestMissingProfile);
  searchController.setOfflineLibrary(&offlineLibrary);
  auto playlistController = std::make_unique<PlaylistController>(&youtubeCatalog, &authManager, &localLibrary);
  playlistController->setOfflineLibrary(&offlineLibrary);
  auto integrations = std::make_unique<Integrations>(&numaAccount, &youtubeCatalog, &authManager);
  auto playback = std::make_unique<PlaybackController>(&youtubeProvider, &authManager, homeController.get(), &systemMedia, integrations.get(), &animatedArtwork, &qobuz);
  QObject::connect(&application, &QCoreApplication::aboutToQuit,
                   playback.get(), &PlaybackController::savePlaybackState);
  playback->setOfflineSources(&downloads, &connectivity);
  auto lyrics = std::make_unique<LyricsController>(&youtubeProvider, &authManager, playback.get());
  auto *discord = integrations->discord();
  // One lyric clock for the karaoke window and Discord; no second tiny conductor.
  QObject::connect(lyrics.get(), &LyricsController::activeLineChanged,
                   discord, &Discord::updateLyric);
  const auto syncDiscordLyrics = [lyrics = lyrics.get(), discord] {
    lyrics->setDiscordLyricsEnabled(discord->enabled() && discord->showSyncedLyrics());
  };
  QObject::connect(discord, &Discord::enabledChanged, lyrics.get(), syncDiscordLyrics);
  QObject::connect(discord, &Discord::showSyncedLyricsChanged, lyrics.get(), syncDiscordLyrics);
  syncDiscordLyrics();
  lyrics->setLocalLibrary(&localLibrary);
  lyrics->setDownloadManager(&downloads);
  // Lyrics in English, courtesy of a 20 MB model that has never heard a song in its life.
  LyricsTranslator lyricsTranslation(lyrics.get());
  const auto syncTranslationLanguage = [&] {
    lyricsTranslation.setTargetLanguage(uiLanguage.effectiveLanguage() == QStringLiteral("es")
        ? QStringLiteral("spanish") : QStringLiteral("english"));
  };
  QObject::connect(&uiLanguage, &UiLanguage::languageChanged, &lyricsTranslation, syncTranslationLanguage);
  syncTranslationLanguage();
  MusicVideoController musicVideo(&youtubeProvider, &authManager, playback.get());
  ArtistVideoBackdrop artistVideo(&youtubeProvider, &authManager);
  // Local imports announce themselves through the same pill as copied links.
  QObject::connect(&localLibrary, &LocalLibrary::noticeRequested, &backend, &BackendInfo::noticeRequested);
  QObject::connect(&downloads, &DownloadManager::noticeRequested, &backend, &BackendInfo::noticeRequested);
  auto libraryActions = std::make_unique<LibraryActions>(&youtubeProvider, &authManager, playback.get(), homeController.get());
  QObject::connect(libraryActions.get(), &LibraryActions::trackLikeChanged,
                   playlistController.get(), &PlaylistController::updateLikedTrack);
  QObject::connect(libraryActions.get(), &LibraryActions::removedFromPlaylist,
                   playlistController.get(), &PlaylistController::removeTrack);
  ListeningParty listeningParty(playback.get(), &authManager);
  CloudLibrary accountLibrary(&numaAccount, playback.get(), homeController.get(), libraryActions.get());
  QObject::connect(playlistController.get(), &PlaylistController::detailChanged, &accountLibrary,
                   [&] { accountLibrary.cacheCollection(playlistController->detail()); });
  QObject::connect(&listeningParty, &ListeningParty::noticeRequested, &backend, &BackendInfo::noticeRequested);
  SystemTray tray(playback.get(), &authManager);
  tray.setAppearance(&appearance);
  QObject::connect(&uiLanguage, &UiLanguage::languageChanged, &tray, &SystemTray::retranslate);
  WindowState windowState;
  ScreenWakeLock screenWake;
  // Talks to the bootstrapper that launched us; inert in development builds.
  BootstrapperClient updates;

  QQmlApplicationEngine engine;
  uiLanguage.setEngine(&engine);
  QObject::connect(&engine, &QQmlEngine::warnings, &application,
                   [](const QList<QQmlError> &warnings) {
                     for (const auto &warning : warnings)
                       QTextStream(stderr) << warning.toString() << '\n';
                   });
  engine.rootContext()->setContextProperty(QStringLiteral("NumaAppearance"), &appearance);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaLanguage"), &uiLanguage);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaAnimatedArtwork"), &animatedArtwork);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaArtworkVideos"), &artworkVideos);
  NoHttp2NetworkFactory networkFactory;
  engine.setNetworkAccessManagerFactory(&networkFactory);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaBackend"),
                                           &backend);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaDocs"), &docs);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaDocsAssistant"), &docsAssistant);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaSystemMedia"),
                                           &systemMedia);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaAuth"),
                                           &authManager);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaAccount"),
                                           &numaAccount);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaSync"), &accountLibrary);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaSupport"), &support);
  // The engine owns the provider; support outlives the engine.
  engine.addImageProvider(QStringLiteral("support"), new SupportImageProvider(&support));
  engine.rootContext()->setContextProperty(QStringLiteral("NumaHome"),
                                           homeController.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaSearch"),
                                           &searchController);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaArtist"), artistController.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaAlbum"),
                                           albumController.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaPlaylist"),
                                           playlistController.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaPlayback"), playback.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaParty"), &listeningParty);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaIntegrations"), integrations.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaQobuz"), &qobuz);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaSpotify"), &spotifyCanvas);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaLyrics"), lyrics.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaTranslation"), &lyricsTranslation);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaMusicVideo"), &musicVideo);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaArtistVideo"), &artistVideo);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaLibrary"), libraryActions.get());
  engine.rootContext()->setContextProperty(QStringLiteral("NumaLocal"), &localLibrary);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaNetwork"), &connectivity);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaDownloads"), &downloads);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaOffline"), &offlineLibrary);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaTray"), &tray);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaWindowState"), &windowState);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaScreenWake"), &screenWake);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaUpdates"), &updates);
  engine.rootContext()->setContextProperty(QStringLiteral("NumaReleaseNotes"),
                                           bundledReleaseNotes());

  connectivity.start();

  // Keep the QML folders intact; relative imports are breadcrumbs, not confetti.
  engine.load(QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/Main.qml")));
  if (engine.rootObjects().isEmpty()) {
    QTextStream(stderr) << "Numa could not load its QML entry point.\n";
    shutdownDeadline.exitCode = 1;
    return 1;
  }

#if defined(__GLIBC__)
  QTimer::singleShot(8000, &application, [] { malloc_trim(0); });
#endif

  if (auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().constFirst())) {
    tray.setWindow(window);
    windowState.setWindow(window);
#ifdef Q_OS_WIN
    systemMedia.start(window->winId());
#else
    if (!systemMedia.attached()) {
      systemMedia.start(window->winId());
    }
#endif
  }
  // A launch during QML loading waits here for a real window to receive it.
  instance.setActivationHandler([&tray] { tray.showWindow(); });

  QObject::connect(&systemMedia, &SystemMediaBridge::commandReceived,
                   [&tray](const QString &kind, const QVariant &) {
    if (kind == QStringLiteral("raise")) {
      tray.showWindow();
    } else if (kind == QStringLiteral("quit")) {
      QGuiApplication::quit();
    }
  });

  shutdownDeadline.exitCode = application.exec();
  return shutdownDeadline.exitCode;
}
