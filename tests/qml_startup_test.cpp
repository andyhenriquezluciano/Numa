#include "account/cloud_cover_crop.h"
#include "local/local_library.h"
#include <QGuiApplication>
#include <QFontDatabase>
#include <QFont>
#include <QQuickWindow>
#include <QQuickItem>
#include <QMouseEvent>
#include <QImage>
#include <QBuffer>
#include <QPluginLoader>
#include <QDir>
#include <QThread>
#include <QTemporaryDir>
#include <QElapsedTimer>
#include <QColor>
#include <QQuickStyle>
#include <QFile>
#include <QQmlComponent>
#include <QQmlEngine>
#include <QJSValue>
#include <QQmlContext>
#include <QVariantMap>
#include <memory>
#include <QTextStream>
#include <QUrl>

int main(int argc, char *argv[]) {
  QGuiApplication application(argc, argv);
  QQuickStyle::setStyle(QStringLiteral("Basic"));
  for (const auto &name : {"regular", "medium", "semibold", "bold"})
    if (QFontDatabase::addApplicationFont(QStringLiteral(":/qt/qml/Numa/app/qml/assets/fonts/inter_%1.ttf").arg(name)) < 0) return 1;
  application.setFont(QFont("Inter"));
  QTextStream output(stderr);
  QFile manifest(QStringLiteral(":/qt/qml/Numa/qmldir"));
  if (!manifest.open(QIODevice::ReadOnly)) {
    output << "Numa QML module manifest is missing.\n";
    return 1;
  }
  const auto contents = manifest.readAll();
  if (contents.contains('\\')) {
    output << "Numa QML module manifest contains Windows path separators.\n";
    return 1;
  }

  QQmlEngine engine;
  engine.rootContext()->setContextProperty(QStringLiteral("NumaAppearance"), QVariantMap{
      {"iconSource", "qrc:/qt/qml/Numa/app/qml/assets/numa-logo-rounded.svg"}, {"theme", "crystal"}, {"rosePine", false}, {"performanceMode", false}, {"borderColor", "#1cffffff"}, {"borderWidth", 1}, {"controlBorderWidth", 1}, {"bordersEnabled", true},
      {"textColor", "#f5f3ee"}, {"mutedColor", "#b4b8b1"}, {"accentColor", "#c4a7e7"}, {"surfaceColor", "#660e1210"}});
  QQmlComponent component(&engine,
      QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/Main.qml")),
      QQmlComponent::PreferSynchronous);
  if (!component.isReady()) {
    for (const auto &error : component.errors())
      output << error.toString() << '\n';
    output << "Numa could not compile its QML entry point.\n";
    return 1;
  }
  QQmlComponent appearanceSection(&engine,
      QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/settings/AppearanceSection.qml")));
  if (!appearanceSection.isReady()) {
    for (const auto &error : appearanceSection.errors()) output << error.toString() << '\n';
    return 1;
  }
  for (const auto &theme : {QStringLiteral("crystal"), QStringLiteral("rose_pine_dawn"), QStringLiteral("rose_pine")}) {
    const bool dawn = theme == QStringLiteral("rose_pine_dawn");
    const bool rose = theme != QStringLiteral("crystal");
    engine.rootContext()->setContextProperty(QStringLiteral("NumaAppearance"), QVariantMap{
        {"theme", theme}, {"rosePine", rose}, {"performanceMode", rose},
        {"playlistSongBackground", false},
        {"overlayColor", dawn ? "#f2e9e1" : "#26233a"}, {"highlightColor", dawn ? "#dfdad9" : "#403d52"},
        {"borderColor", rose ? "#ebbcba" : "#1cffffff"}, {"borderWidth", rose ? 2 : 1},
        {"controlBorderWidth", rose ? 0 : 1},
        {"bordersEnabled", true},
        {"textColor", dawn ? "#575279" : rose ? "#e0def4" : "#f5f3ee"}, {"mutedColor", dawn ? "#797593" : "#908caa"}, {"accentColor", dawn ? "#d7827e" : "#c4a7e7"}, {"surfaceColor", dawn ? "#fffaf3" : "#1f1d2e"}});
  QQmlComponent frame(&engine);
  frame.setData("import QtQuick\nimport Numa\nRoundedSurfaceFrame {}", QUrl());
  std::unique_ptr<QObject> surface(frame.create());
  if (!surface) {
    for (const auto &error : frame.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *border = surface->property("border").value<QObject *>();
  if (!border || border->property("width").toInt() != (rose ? 0 : 1)) {
    output << "Controls must be borderless in Rose Pine.\n";
    return 1;
  }
  for (bool active : {false, true}) {
    engine.rootContext()->setContextProperty(QStringLiteral("NumaParty"), QVariantMap{
      {"active", active}, {"connected", active}, {"host", active}, {"code", "ABC234"},
      {"message", ""}, {"peers", QVariantList{QVariantMap{{"id", "guest"}, {"name", "Android"}}}}});
    QQmlComponent party(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/player/ListeningPartyPanel.qml")));
    std::unique_ptr<QObject> panel(party.create());
    if (!panel) {
      for (const auto &error : party.errors()) output << error.toString() << '\n';
      return 1;
    }
    auto *code = panel->findChild<QObject *>(QStringLiteral("partyRoomCode"));
    if (!code) return 1;
    code->setProperty("text", QStringLiteral("ab23cd"));
    QMetaObject::invokeMethod(code, "textEdited");
    if (code->property("text").toString() != QStringLiteral("AB23CD")) {
      output << "Party room codes must display uppercase.\n";
      return 1;
    }
  }
  }
  QQmlComponent wheel(&engine);
  wheel.setData("import QtQuick\nimport Numa\nItem { width: 100; height: 100; Flickable { id: list; objectName: 'list'; width: 100; height: 100; contentHeight: 300 } WheelGlide { objectName: 'wheel'; view: list; overshootEnabled: false } }", QUrl());
  std::unique_ptr<QObject> scrollTest(wheel.create());
  if (!scrollTest) {
    for (const auto &error : wheel.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *glide = scrollTest->findChild<QObject *>("wheel");
  auto *list = scrollTest->findChild<QObject *>("list");
  if (!glide || !list) return 1;
  for (const auto [requested, expected] : {std::pair{-90.0, 0.0}, {290.0, 200.0}, {85.0, 85.0}}) {
    if (!QMetaObject::invokeMethod(glide, "place", Q_ARG(QVariant, QVariant(requested)))
        || list->property("contentY").toDouble() != expected) {
      output << "Bounded wheel scrolling must stay inside its viewport.\n";
      return 1;
    }
  }
  QImage source(600, 400, QImage::Format_RGB32);
  source.fill(Qt::red);
  for (int y = 0; y < source.height(); ++y)
    for (int x = 300; x < source.width(); ++x) source.setPixelColor(x, y, Qt::blue);
  const auto cropped = cloud::cropCover(source, QRectF(0.5, 0, 0.5, 0.75));
  if (cropped.size() != QSize(1024, 1024) || cropped.pixelColor(512, 512) != QColor(Qt::blue)
      || !cloud::cropCover(source, QRectF(2, 2, 0.5, 0.5)).isNull()) {
    output << "Playlist artwork must crop the selected region into a square.\n"; return 1;
  }
  const auto runtimeRoot = qEnvironmentVariable("NUMA_STAGED_RUNTIME_ROOT");
  QPluginLoader webpPlugin;
  if (!runtimeRoot.isEmpty()) {
    webpPlugin.setFileName(runtimeRoot + "/imageformats/qwebp.dll");
    if (!webpPlugin.load()) { output << webpPlugin.errorString() << '\n'; return 1; }
  }
  QByteArray webpBytes;
  QBuffer webpBuffer(&webpBytes); webpBuffer.open(QIODevice::WriteOnly);
  if (!cropped.save(&webpBuffer, "WEBP", 85) || !webpBytes.startsWith("RIFF")
      || QImage::fromData(webpBytes, "WEBP").size() != cropped.size()) {
    output << "Playlist covers require working WebP encoding and decoding.\n"; return 1;
  }
  QQmlComponent mockSync(&engine);
  mockSync.setData(R"(
    import QtQuick
    QtObject {
      property int revision: 0
      property var playlistItems: []
      function section(key) { return key === "playlists" ? playlistItems : [] }
      function metricArtistArtwork(name) { return "" }
      function listeningMetrics(days) {
        return {listens: 6, heardMs: 180000, songs: 1,
                tracks: [{id: "test", title: "Song", artist: "Artist", listenCount: 6}],
                artists: [{title: "Artist", listenCount: 6, songCount: 1}],
                albums: [{title: "Album", listenCount: 6, songCount: 1}]};
      }
      function visiblePlaylists(items) { return items }
      function artworkFor(id, fallback) { return fallback }
      property bool saved: false
      property bool automatic: false
      property bool busy: false
      property string status: "Ready"
      function syncNow() { busy = true; status = "Syncing" }
      property bool defaultsHidden: true
      property bool failArtworkSave: true
      property string coverError: "The image could not be saved."
      property rect savedCrop
      function setCoverCrop(id, url, area) { savedCrop = area; return !failArtworkSave }
      function isHiddenPlaylist(id) { return defaultsHidden }
      function restoreDefaultPlaylists() { defaultsHidden = false; revision++ }
      function isSaved(kind, id) { return saved }
      function toggleSaved(kind, media) { saved = !saved; revision++ }
      function removePlaylist(id) { saved = false; revision++ }
      function automaticCover(id) { return automatic }
      function setAutomaticCover(id, enabled) { automatic = enabled; revision++ }
    }
  )", QUrl());
  std::unique_ptr<QObject> sync(mockSync.create());
  if (!sync) return 1;
  engine.rootContext()->setContextProperty("NumaSync", sync.get());
  QQmlComponent networkMock(&engine);
  networkMock.setData(R"(
    import QtQuick
    QtObject {
      property bool offline: true
      property bool retrying: false
      property bool checking: false
      property int retries: 0
      function retry() { retries++; checking = true }
    }
  )", QUrl());
  std::unique_ptr<QObject> network(networkMock.create());
  if (!network) return 1;
  engine.rootContext()->setContextProperty("NumaNetwork", network.get());
  QQmlComponent offlineButtonComponent(&engine);
  offlineButtonComponent.setData("import QtQuick\nimport Numa\nOfflineStatusButton {}", QUrl());
  std::unique_ptr<QObject> offlineButton(offlineButtonComponent.create());
  if (!offlineButton) return 1;
  auto *offlineIcon = offlineButton->findChild<QObject *>("offlineConnectionIcon");
  if (!offlineIcon || !offlineButton->property("visible").toBool()) return 1;
  QMetaObject::invokeMethod(offlineButton.get(), "clicked");
  if (!network->property("checking").toBool() || offlineButton->property("enabled").toBool()) return 1;
  for (int i = 0; i < 20; ++i) { application.processEvents(); QThread::msleep(10); }
  network->setProperty("checking", false);
  if (offlineIcon->property("rotation").toDouble() != 0
      || offlineIcon->property("name").toString() != "wifi-off"
      || offlineIcon->property("color").value<QColor>() != QColor("#c4a7e7")
      || !offlineButton->property("enabled").toBool()) {
    output << "The offline icon must return upright with a purple accent after checking the connection.\n";
    return 1;
  }
  network->setProperty("offline", false);
  if (offlineButton->property("visible").toBool()) return 1;
  network->setProperty("checking", true);
  if (offlineButton->property("visible").toBool()) return 1;
  network->setProperty("checking", false);
  network->setProperty("offline", true);
  QTemporaryDir artworkDirectory;
  if (!artworkDirectory.isValid()) return 1;
  QImage firstCover(64, 64, QImage::Format_RGB32);
  firstCover.fill(Qt::red);
  QImage nextCover(64, 64, QImage::Format_RGB32);
  nextCover.fill(Qt::blue);
  const auto firstCoverPath = artworkDirectory.filePath("first.png");
  const auto nextCoverPath = artworkDirectory.filePath("next.png");
  if (!firstCover.save(firstCoverPath) || !nextCover.save(nextCoverPath)) return 1;
  const auto firstCoverUrl = QUrl::fromLocalFile(firstCoverPath);
  const auto nextCoverUrl = QUrl::fromLocalFile(nextCoverPath);
  QQmlComponent artworkComponent(&engine);
  artworkComponent.setData("import QtQuick\nimport Numa\nRoundedArtwork { width: 64; height: 64 }", QUrl());
  std::unique_ptr<QObject> artwork(artworkComponent.create());
  if (!artwork) return 1;
  QObject *artworkImage = nullptr;
  for (auto *child : artwork->children())
    if (child->metaObject()->indexOfProperty("retainWhileLoading") >= 0) artworkImage = child;
  if (!artworkImage) return 1;
  const auto waitForCover = [&](const QUrl &expected) {
    QElapsedTimer timeout;
    timeout.start();
    while (timeout.elapsed() < 3000) {
      application.processEvents();
      if (artworkImage->property("source").toUrl() == expected
          && artworkImage->property("status").toInt() == 1) return true;
      QThread::msleep(5);
    }
    return false;
  };
  artwork->setProperty("source", firstCoverUrl);
  if (!waitForCover(firstCoverUrl)) return 1;
  QMetaObject::invokeMethod(artwork.get(), "reload");
  if (!waitForCover(firstCoverUrl)) return 1;
  artwork->setProperty("source", nextCoverUrl);
  if (!waitForCover(nextCoverUrl)) {
    output << "Retrying a cover must preserve updates when the playing song changes.\n";
    return 1;
  }
  QMetaObject::invokeMethod(artwork.get(), "reload");
  artwork->setProperty("source", firstCoverUrl);
  if (!waitForCover(firstCoverUrl)) {
    output << "A pending cover retry must load the current song instead of an older one.\n";
    return 1;
  }
  QQmlComponent mockAccount(&engine);
  mockAccount.setData("import QtQuick\nQtObject { property bool isSignedIn: false }", QUrl());
  std::unique_ptr<QObject> account(mockAccount.create());
  if (!account) return 1;
  engine.rootContext()->setContextProperty("NumaAccount", account.get());
  QQmlComponent controls(&engine);
  controls.setData(R"(
    import QtQuick
    import QtQuick.Controls
    import Numa
    ApplicationWindow {
      width: 700; height: 600
      color: "#181622"
      CloudCollectionAction { x: 50; y: 50; media: ({id: "PL123", title: "Playlist", type: "playlist"}) }
      SettingsSegmented {
        objectName: "testChoice"; x: 50; y: 120
        currentValue: "adaptive"
        model: [{value: "standard", label: "Standard"}, {value: "adaptive", label: "Adaptive", enabled: false}]
      }
      CloudSyncSetting { x: 330; y: 330; width: 300 }
      RestoreDefaultPlaylistsButton { objectName: "restoreDefaults"; x: 50; y: 180; width: 230 }
      SettingsInfoButton { objectName: "testInfo"; x: 280; y: 120; heading: "Crossfade"; message: "Blends consecutive songs." }
      SettingsSegmented {
        objectName: "singleChoice"; x: 330; y: 120
        currentValue: "apple_music"
        model: [{value: "apple_music", label: "Apple Music"}]
      }
    }
  )", QUrl());
  std::unique_ptr<QObject> controlsObject(controls.create());
  if (!controlsObject) {
    for (const auto &error : controls.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *window = qobject_cast<QQuickWindow *>(controlsObject.get());
  auto *libraryButton = controlsObject->findChild<QObject *>("cloudLibraryButton");
  auto *artworkButton = controlsObject->findChild<QObject *>("playlistArtworkButton");
  auto *menu = controlsObject->findChild<QObject *>("playlistArtworkMenu");
  auto *choice = controlsObject->findChild<QObject *>("testChoice");
  if (!window || !libraryButton || !artworkButton || !menu || !choice) return 1;
  auto *syncButton = controlsObject->findChild<QObject *>("syncLibraryNow");
  if (!syncButton || syncButton->property("enabled").toBool()) return 1;
  account->setProperty("isSignedIn", true);
  if (!syncButton->property("enabled").toBool()) return 1;
  QMetaObject::invokeMethod(syncButton, "clicked");
  if (!sync->property("busy").toBool() || syncButton->property("enabled").toBool()) return 1;
  sync->setProperty("busy", false);
  auto *restore = controlsObject->findChild<QObject *>("restoreDefaults");
  if (!restore || !restore->property("enabled").toBool()) return 1;
  QMetaObject::invokeMethod(restore, "clicked");
  if (sync->property("defaultsHidden").toBool() || restore->property("enabled").toBool()) return 1;
  QMetaObject::invokeMethod(libraryButton, "clicked");
  if (!sync->property("saved").toBool() || libraryButton->property("glyph").toString() != "check") return 1;
  auto *removeConfirmation = controlsObject->findChild<QObject *>("removePlaylistConfirmation");
  if (!removeConfirmation) return 1;
  QMetaObject::invokeMethod(libraryButton, "clicked");
  if (!sync->property("saved").toBool() || !removeConfirmation->property("visible").toBool()) {
    output << "Removing a saved playlist must ask for confirmation without changing the library.\n";
    return 1;
  }
  QMetaObject::invokeMethod(removeConfirmation, "close");
  if (!sync->property("saved").toBool()) return 1;
  QMetaObject::invokeMethod(libraryButton, "clicked");
  QMetaObject::invokeMethod(removeConfirmation, "accepted");
  QMetaObject::invokeMethod(removeConfirmation, "close");
  if (sync->property("saved").toBool() || libraryButton->property("glyph").toString() != "library-big") return 1;
  const QColor neutralIcon(engine.rootContext()->contextProperty("NumaAppearance").toMap().value("textColor").toString());
  if (libraryButton->property("width") != artworkButton->property("width")
      || libraryButton->property("glyphColor").value<QColor>() != neutralIcon
      || artworkButton->property("glyphColor").value<QColor>() != neutralIcon) {
    output << "Collection actions must share neutral icon colors and dimensions.\n"; return 1;
  }
  auto *changeEntry = controlsObject->findChild<QObject *>("playlistArtworkChange");
  auto *automaticEntry = controlsObject->findChild<QObject *>("playlistArtworkAutomatic");
  if (!changeEntry || !automaticEntry || changeEntry->property("leftPadding") != automaticEntry->property("leftPadding")) {
    output << "Playlist menu entries must share text alignment.\n"; return 1;
  }
  if (choice->property("currentIndex").toInt() != 1 || choice->property("displayText").toString() != "Adaptive") return 1;
  window->show();
  QMetaObject::invokeMethod(artworkButton, "clicked");
  menu->setProperty("currentIndex", 0);
  for (int i = 0; i < 25; ++i) { application.processEvents(); QThread::msleep(10); }
  if (!menu->property("visible").toBool() || menu->property("width").toInt() < 250 || menu->property("height").toInt() < 150) {
    output << "Playlist actions must open as a full-sized menu.\n"; return 1;
  }
  const auto captureDir = qEnvironmentVariable("NUMA_UI_CAPTURE_DIR");
  if (!captureDir.isEmpty()) {
    QDir().mkpath(captureDir);
    if (!window->grabWindow().save(captureDir + "/playlist-menu.png")) return 1;
  }
  QMetaObject::invokeMethod(menu, "close");
  auto *crop = controlsObject->findChild<QObject *>("playlistCropDialog");
  if (!crop) return 1;
  QImage image(600, 400, QImage::Format_RGB32); image.fill(QColor("#f5a080"));
  const auto testImage = QDir::tempPath() + "/numa-cover-crop-test.png";
  if (!image.save(testImage)) return 1;
  crop->setProperty("imageSource", QUrl::fromLocalFile(testImage));
  QMetaObject::invokeMethod(crop, "open");
  for (int i = 0; i < 25; ++i) { application.processEvents(); QThread::msleep(10); }
  if (!crop->property("visible").toBool() || crop->property("width").toInt() < 300) return 1;
  if (!captureDir.isEmpty() && !window->grabWindow().save(captureDir + "/playlist-crop.png")) return 1;
  auto *saveArtwork = crop->findChild<QObject *>("savePlaylistArtwork");
  if (!saveArtwork) return 1;
  QMetaObject::invokeMethod(saveArtwork, "clicked");
  if (!crop->property("visible").toBool() || crop->property("errorText").toString().isEmpty()) {
    output << "Failed artwork saves must keep the editor open with an error.\n"; return 1;
  }
  sync->setProperty("failArtworkSave", false);
  QMetaObject::invokeMethod(saveArtwork, "clicked");
  if (crop->property("visible").toBool() || !sync->property("savedCrop").toRectF().isValid()) return 1;
  QFile::remove(testImage);
  auto *singleChoice = controlsObject->findChild<QObject *>("singleChoice");
  auto *singlePicker = singleChoice ? singleChoice->findChild<QObject *>("settingsChoicePicker") : nullptr;
  if (!singlePicker || singlePicker->property("visible").toBool()
      || singleChoice->property("displayText").toString() != "Apple Music") {
    output << "A single available choice must display as text, without a dropdown.\n"; return 1;
  }
  auto *infoButton = controlsObject->findChild<QObject *>("testInfo");
  auto *information = controlsObject->findChild<QObject *>("settingsInformationPopup");
  if (!infoButton || !information) return 1;
  QMetaObject::invokeMethod(infoButton, "clicked");
  for (int i = 0; i < 25; ++i) { application.processEvents(); QThread::msleep(10); }
  if (!information->property("visible").toBool() || information->property("height").toDouble() < 150) {
    output << "Settings help must show the title, explanation and close button in one card.\n"; return 1;
  }
  if (!captureDir.isEmpty() && !window->grabWindow().save(captureDir + "/settings-information.png")) return 1;
  QMetaObject::invokeMethod(information, "close");
  window->close();
  if (!captureDir.isEmpty()) {
    engine.rootContext()->setContextProperty("NumaAppearance", QVariantMap{
      {"iconSource", "qrc:/qt/qml/Numa/app/qml/assets/numa-logo-rounded.svg"}, {"theme", "rose_pine_dawn"}, {"rosePine", true}, {"performanceMode", true}, {"layoutStyle", "glade"},
      {"baseColor", "#faf4ed"}, {"surfaceColor", "#fffaf3"}, {"overlayColor", "#f2e9e1"}, {"highlightColor", "#efd4cf"},
      {"textColor", "#575279"}, {"mutedColor", "#797593"}, {"accentColor", "#d7827e"},
      {"borderColor", "#d7827e"}, {"bordersEnabled", false}, {"borderWidth", 0}, {"controlBorderWidth", 0},
      {"animatedArtworkEnabled", false}, {"animatedCollageEnabled", false}, {"showBitrate", false}});
    engine.rootContext()->setContextProperty("NumaPlayback", QVariantMap{
      {"track", QVariantMap{{"id", "preview"}, {"title", "Let It Happen"}, {"artist", "Tame Impala"}, {"thumbnail", ""}}},
      {"transitionTrack", QVariantMap{{"thumbnail", ""}}}, {"crossfadeActive", false}, {"crossfadeProgress", 0},
      {"playing", true}, {"loading", false}, {"canGoNext", true}, {"canGoPrevious", true},
      {"shuffleEnabled", false}, {"repeatMode", "off"}, {"duration", 460}, {"displayDuration", 460}, {"displayPosition", 228}, {"errorMessage", ""}, {"bitrate", 0}});
    engine.rootContext()->setContextProperty("NumaHome", QVariantMap{{"volume", 0.7}, {"playlists", QVariantList{}}, {"playlistsError", ""}, {"playlistsLoading", false}});
    engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"playlists", QVariantList{}}});
    engine.rootContext()->setContextProperty("NumaOffline", QVariantMap{{"playlists", QVariantList{}}});
    engine.rootContext()->setContextProperty("NumaPlaylist", QVariantMap{{"detail", QVariantMap{}}});
    engine.rootContext()->setContextProperty("NumaAuth", QVariantMap{{"isSignedIn", true}});
    engine.rootContext()->setContextProperty("NumaLibrary", QVariantMap{{"currentLiked", true}, {"likeBusy", false}});
    engine.rootContext()->setContextProperty("NumaMusicVideo", QVariantMap{{"status", ""}, {"available", false}});
    QQmlComponent dawnPreview(&engine);
    dawnPreview.setData(R"(
      import QtQuick
      import QtQuick.Controls
      import Numa
      ApplicationWindow {
        id: preview
        width: 1160; height: 600; color: NumaAppearance.baseColor
        Item { id: backdrop; anchors.fill: parent }
        NavigationSidebar { objectName: "dawnSidebar"; width: 220; height: 500; currentPage: "library" }
        SettingsSelect { x: 800; y: 270; width: 220; model: ["English", "Español"]; currentIndex: 1; Component.onCompleted: popup.open() }
        SettingsNavItem { x: 540; y: 270; width: 200; current: true; entry: ({key: "general", label: "General", icon: "sliders-horizontal"}) }
        SettingsNavItem { x: 540; y: 312; width: 200; entry: ({key: "accounts", label: "Cuentas", icon: "circle-user-round"}) }
        CollectionHero { x: 250; y: 20; width: 260; title: "Música que me gusta"; kind: "Lista de reproducción"; byline: "Edsen"; meta: ["211 canciones", "12 h 21 min"]; gradientCover: true }
        LibraryHeader { x: 540; y: 26; width: 590 }
        LibraryPill { x: 550; y: 110; text: "Todo"; checked: true }
        LibraryPill { x: 620; y: 110; text: "Métricas" }
        MediaCard { x: 550; y: 170; width: 290; presentation: "songlist"; media: ({id: "other", title: "Let It Happen", artist: "Tame Impala", type: "song"}) }
        PlayerBar { objectName: "dawnPlayer"; x: 20; y: 520; width: 1120; height: 64; backdrop: backdrop }
      }
    )", QUrl());
    std::unique_ptr<QObject> dawnObject(dawnPreview.create());
    auto *dawnWindow = qobject_cast<QQuickWindow *>(dawnObject.get());
    if (!dawnWindow) {
      for (const auto &error : dawnPreview.errors()) output << error.toString() << '\n';
      return 1;
    }
    auto *sidebar = dawnObject->findChild<QObject *>("dawnSidebar");
    QVariant same;
    QMetaObject::invokeMethod(sidebar, "samePlaylist", Q_RETURN_ARG(QVariant, same),
        Q_ARG(QVariant, QVariant(QVariantMap{{"id", "offline-playlist:LM"}})),
        Q_ARG(QVariant, QVariant(QVariantMap{{"id", "FEmusic_liked_videos"}})));
    if (!same.toBool()) { output << "Offline likes must keep the same playlist identity.\n"; return 1; }
    QVariant visible;
    QMetaObject::invokeMethod(sidebar, "visiblePlaylists", Q_RETURN_ARG(QVariant, visible),
        Q_ARG(QVariant, QVariant(QVariantList{QVariantMap{{"id", "offline-playlist:LM"}}, QVariantMap{{"id", "VLLM"}}})));
    if (visible.value<QJSValue>().property("length").toInt() != 1) return 1;
    sync->setProperty("playlistItems", QVariantList{
      QVariantMap{{"id", "local-playlist:deleted"}, {"title", "Deleted copy"}},
      QVariantMap{{"id", "local-playlist:existing"}, {"title", "Stale metadata"}},
      QVariantMap{{"id", "PLremote"}, {"title", "Remote"}}});
    QMetaObject::invokeMethod(sidebar, "visiblePlaylists", Q_RETURN_ARG(QVariant, visible),
        Q_ARG(QVariant, QVariant(QVariantList{
          QVariantMap{{"id", "local-playlist:existing"}, {"source", "local"}, {"title", "Device copy"}},
          QVariantMap{{"id", "local-playlist:deleted"}, {"title", "Stale home entry"}}})));
    const auto merged = visible.value<QJSValue>();
    if (merged.property("length").toInt() != 2
        || merged.property(0).property("title").toString() != "Device copy"
        || merged.property(1).property("id").toString() != "PLremote") {
      output << "Deleted local playlists must not be resurrected from cloud or cached home metadata.\n";
      return 1;
    }
    sync->setProperty("playlistItems", QVariantList{});
    dawnWindow->show();
    for (int i = 0; i < 35; ++i) { application.processEvents(); QThread::msleep(10); }
    auto *brand = dawnObject->findChild<QObject *>("applicationBrand");
    auto *dawnPlayer = dawnObject->findChild<QObject *>("dawnPlayer");
    if (!brand || brand->property("color").value<QColor>() != QColor("#575279")
        || !dawnPlayer || dawnPlayer->property("controlColor").value<QColor>() != QColor("#575279")
        || dawnPlayer->property("progressColor") == dawnPlayer->property("trackColor")) {
      output << "Dawn must provide contrasting branding, player controls and progress.\n";
      return 1;
    }
    if (!dawnWindow->grabWindow().save(captureDir + "/rose-pine-dawn.png")) return 1;
    dawnWindow->close();
  }
  QQmlComponent metricsComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/views/library/ListeningMetrics.qml")));
  std::unique_ptr<QObject> metricsObject(metricsComponent.create());
  if (!metricsObject) {
    for (const auto &error : metricsComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  metricsObject->setProperty("width", 900);
  metricsObject->setProperty("height", 650);
  metricsObject->setProperty("period", 7);
  application.processEvents();
  engine.rootContext()->setContextProperty("NumaOffline", QVariantMap{{"songs", QVariantList{}}, {"playlists", QVariantList{}}});
  QQmlComponent offlineMetrics(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/views/offline/OfflineLibraryView.qml")));
  std::unique_ptr<QObject> offlineMetricsObject(offlineMetrics.create());
  if (!offlineMetricsObject) {
    for (const auto &error : offlineMetrics.errors()) output << error.toString() << '\n';
    return 1;
  }
  offlineMetricsObject->setProperty("filter", "metrics");
  auto *offlineDashboard = offlineMetricsObject->findChild<QObject *>("offlineListeningMetrics");
  if (!offlineDashboard || !offlineDashboard->property("visible").toBool()) {
    output << "Listening metrics must remain accessible offline.\n";
    return 1;
  }
  engine.rootContext()->setContextProperty("NumaPlayback", QVariantMap{
    {"crossfadeEnabled", false}, {"adaptiveMix", QVariantMap{{"mode", "standard"}}},
    {"bestMixSorted", false}, {"bestMix", QVariantMap{{"error", ""}, {"busy", false},
      {"sorting", false}, {"downloading", false}, {"downloaded", 0}, {"completed", 0}, {"total", 0}}}});
  QQmlComponent queuePaneComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/player/FullscreenQueuePane.ui.qml")));
  std::unique_ptr<QObject> queuePane(queuePaneComponent.create());
  if (!queuePane) {
    for (const auto &error : queuePaneComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  queuePane->setProperty("historyItems", QVariantList{QVariantMap{{"id", "played"}}});
  queuePane->setProperty("currentTrack", QVariantMap{{"id", "playing"}});
  queuePane->setProperty("queueItems", QVariantList{QVariantMap{{"id", "next"}}});
  queuePane->setProperty("continuous", true);
  auto *queueList = queuePane->property("list").value<QObject *>();
  auto *clearQueue = queuePane->property("clearButton").value<QObject *>();
  application.processEvents();
  const auto queueRows = queuePane->property("rowItems").value<QJSValue>().toVariant().toList();
  if (!queueList || !clearQueue || queueList->property("count").toInt() != 3
      || queuePane->property("queueStart").toInt() != 2 || queueRows.size() != 3
      || queueRows.at(0).toMap().value("id") != "played"
      || queueRows.at(1).toMap().value("id") != "playing"
      || queueRows.at(2).toMap().value("id") != "next") {
    output << "Fullscreen continuous queue must include history, current song and upcoming songs in order.\n";
    return 1;
  }
  queuePane->setProperty("historyItems", QVariantList{
    QVariantMap{{"id", "old-one"}}, QVariantMap{{"id", "old-two"}},
    QVariantMap{{"id", "recent-one"}}, QVariantMap{{"id", "recent-two"}}});
  application.processEvents();
  const auto recentRows = queuePane->property("rowItems").value<QJSValue>().toVariant().toList();
  if (queuePane->property("historyOffset").toInt() != 2 || queuePane->property("historyCount").toInt() != 2
      || recentRows.size() != 4 || recentRows.at(0).toMap().value("id") != "recent-one"
      || recentRows.at(1).toMap().value("id") != "recent-two"
      || recentRows.at(2).toMap().value("id") != "playing") {
    output << "Continuous queue must show only the two most recently played songs before the current one.\n";
    return 1;
  }
  queuePane->setProperty("historyItems", QVariantList{QVariantMap{{"id", "played"}}});
  queuePane->setProperty("dragIndex", 0);
  application.processEvents();
  auto *dragPreview = queuePane->findChild<QObject *>("fullscreenQueueDragPreview");
  if (queueList->property("interactive").toBool() || !dragPreview
      || !dragPreview->property("dragPreview").toBool() || !dragPreview->property("visible").toBool()) {
    output << "Fullscreen queue dragging must show its normal row preview and suspend list gestures.\n";
    return 1;
  }
  queuePane->setProperty("dragIndex", -1);
  QQmlComponent draggableRowComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/player/FullscreenQueueRow.ui.qml")));
  std::unique_ptr<QObject> draggableRow(draggableRowComponent.create());
  if (!draggableRow) return 1;
  auto *rowContent = draggableRow->property("contentItem").value<QObject *>();
  for (const auto &section : {QString(), QStringLiteral("UP NEXT")}) {
    draggableRow->setProperty("sectionLabel", section);
    application.processEvents();
    const qreal header = draggableRow->property("sectionHeight").toReal();
    const qreal frameCenter = header + (draggableRow->property("height").toReal() - header) / 2;
    const qreal contentCenter = rowContent ? rowContent->property("y").toReal() + rowContent->property("height").toReal() / 2 : -1;
    if (qAbs(frameCenter - contentCenter) > 0.5) {
      output << "Queue row content must be centered inside its frame, including section headers.\n";
      return 1;
    }
  }
  draggableRow->setProperty("dragPane", QVariant::fromValue(queuePane.get()));
  draggableRow->setProperty("queueIndex", 0);
  if (!draggableRow->property("draggable").toBool()) return 1;
  draggableRow->setProperty("previous", true);
  if (draggableRow->property("draggable").toBool()) return 1;
  draggableRow->setProperty("previous", false);
  draggableRow->setProperty("current", true);
  if (draggableRow->property("draggable").toBool()) return 1;
  draggableRow->setProperty("current", false);
  draggableRow->setProperty("dragPreview", true);
  if (draggableRow->property("draggable").toBool()) return 1;
  queuePane->setProperty("queueItems", QVariantList{});
  application.processEvents();
  if (queueList->property("count").toInt() != 2 || clearQueue->property("enabled").toBool()) {
    output << "An empty upcoming queue must preserve history and the current song without enabling Clear.\n";
    return 1;
  }
  queuePane->setProperty("continuous", false);
  queuePane->setProperty("queueItems", QVariantList{QVariantMap{{"id", "next"}}});
  application.processEvents();
  if (queueList->property("count").toInt() != 1 || queuePane->property("queueStart").toInt() != 0) {
    output << "Standard fullscreen queue must contain upcoming songs only.\n";
    return 1;
  }
  QQmlComponent playlistArtworkComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/PlaylistArtwork.qml")));
  std::unique_ptr<QObject> playlistArtwork(playlistArtworkComponent.create());
  if (!playlistArtwork) {
    for (const auto &error : playlistArtworkComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  for (const auto &entry : {qMakePair(QStringLiteral("VLLM"), QStringLiteral("liked")),
                           qMakePair(QStringLiteral("offline-playlist:SE"), QStringLiteral("episodes")),
                           qMakePair(QStringLiteral("local-playlist:example"), QStringLiteral("local"))}) {
    playlistArtwork->setProperty("media", QVariantMap{{"id", entry.first}, {"title", "Example"},
      {"thumbnail", "https://example.invalid/old-cover.jpg"}});
    if (playlistArtwork->property("kind").toString() != entry.second || !playlistArtwork->property("generated").toBool()) {
      output << "Default and local playlists must use a stable themed cover.\n";
      return 1;
    }
  }
  playlistArtwork->setProperty("customArtwork", QStringLiteral("qrc:/qt/qml/Numa/app/qml/assets/lucide/list-music.svg"));
  if (playlistArtwork->property("generated").toBool()) {
    output << "A custom playlist cover must take priority over generated artwork.\n";
    return 1;
  }
  playlistArtwork->setProperty("customArtwork", QString());
  playlistArtwork->setProperty("media", QVariantMap{{"id", "local-playlist:example"}, {"hasCustomCover", true},
    {"thumbnail", "qrc:/qt/qml/Numa/app/qml/assets/lucide/list-music.svg"}});
  if (playlistArtwork->property("generated").toBool()) return 1;
  sync->setProperty("saved", false);
  engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"playlists", QVariantList{
    QVariantMap{{"id", "local-playlist:existing"}, {"title", "Local list"}}}}});
  QQmlComponent localCollectionComponent(&engine);
  localCollectionComponent.setData("import QtQuick\nimport Numa\nCloudCollectionAction { media: ({id: 'local-playlist:existing', source: 'local', type: 'playlist', title: 'Local list'}) }", QUrl());
  std::unique_ptr<QObject> localCollection(localCollectionComponent.create());
  if (!localCollection) return 1;
  auto *localLibraryButton = localCollection->findChild<QObject *>("cloudLibraryButton");
  if (!localCollection->property("saved").toBool() || !localLibraryButton
      || localLibraryButton->property("glyph").toString() != "check"
      || !localLibraryButton->property("enabled").toBool() || sync->property("saved").toBool()) {
    output << "Existing local playlists must be marked as saved independently of cloud-library membership.\n";
    return 1;
  }
  engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"playlists", QVariantList{}}});
  if (localCollection->property("saved").toBool() || localLibraryButton->property("enabled").toBool()) {
    output << "Local collection state must update when the playlist leaves the device library.\n";
    return 1;
  }
  engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"songs", QVariantList{QVariantMap{{"id", "local:test"}}}}});
  QQmlComponent localOptionsComponent(&engine);
  localOptionsComponent.setData(R"(
import QtQuick
import Numa
LocalMediaDownloadOptions {
    width: 600
    downloads: QtObject {
        property bool lyrics: true
        property bool translation: false
        property bool artwork: true
        property bool busy: false
        property int total: 0
        property int completed: 0
        property string message: ""
    }
}
)", QUrl());
  std::unique_ptr<QObject> localOptions(localOptionsComponent.create());
  if (!localOptions) {
    for (const auto &error : localOptionsComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  qobject_cast<QQuickItem *>(localOptions.get())->setParentItem(window->contentItem());
  application.processEvents();
  const auto findVisual = [](auto &&self, QQuickItem *item, const QString &name) -> QQuickItem * {
    if (item->objectName() == name) return item;
    for (auto *child : item->childItems()) if (auto *match = self(self, child, name)) return match;
    return nullptr;
  };
  for (int i = 0; i < 3; ++i) {
    auto *toggle = findVisual(findVisual, qobject_cast<QQuickItem *>(localOptions.get()), QStringLiteral("localDownloadSwitch%1").arg(i));
    if (!toggle || toggle->property("checked").toBool() != (i != 1)) {
      output << "Local download switches must reflect the selected options: " << i << " found=" << bool(toggle) << " checked=" << (toggle ? toggle->property("checked").toBool() : false) << "\n";
      return 1;
    }
  }
  QQmlComponent localMenuComponent(&engine);
  localMenuComponent.setData(R"(
import QtQuick
import Numa
MediaMenu {
    property var requestedAlbum: ({})
    property var requestedArtist: ({})
    media: ({id: 'local:test', type: 'song', title: 'Song', artist: 'Artist', album: 'Album', localPlaylistId: 'local-playlist:test'})
    onAlbumRequested: album => requestedAlbum = album
    onArtistRequested: artist => requestedArtist = artist
}
)", QUrl());
  std::unique_ptr<QObject> localMenu(localMenuComponent.create());
  if (!localMenu) return 1;
  QMetaObject::invokeMethod(localMenu.get(), "viewAlbum");
  QMetaObject::invokeMethod(localMenu.get(), "viewArtist", Q_ARG(QVariant, QVariant()), Q_ARG(QVariant, QVariant()), Q_ARG(QVariant, QVariant()));
  if (!localMenu->property("requestedAlbum").value<QJSValue>().toVariant().toMap().value("lookupByName").toBool()
      || !localMenu->property("requestedArtist").value<QJSValue>().toVariant().toMap().value("lookupByName").toBool()) {
    output << "Local songs must resolve album and artist pages by name.\n";
    return 1;
  }
  for (auto *entry : localMenu->findChildren<QObject *>()) {
    const QString text = entry->property("text").toString();
    if (text == QStringLiteral("Set cover…") || text == QStringLiteral("Set lyrics…") || text == QStringLiteral("Remove from library")) {
      output << "Local song menus must not include removed editing actions.\n";
      return 1;
    }
  }
  if (localOptions->property("implicitHeight").toDouble() < 150) {
    output << "Local download options must have a visible layout.\n";
    return 1;
  }
  QQmlComponent localBadgesComponent(&engine);
  localBadgesComponent.setData(R"(
import QtQuick
import Numa
CollectionTrackRow {
    width: 700
    showLocalExtras: true
    track: ({id: 'local:test', title: 'Test song', hasDownloadedLyrics: true, hasDownloadedArtwork: true})
}
)", QUrl());
  std::unique_ptr<QObject> localBadges(localBadgesComponent.create());
  if (!localBadges) {
    for (const auto &error : localBadgesComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *lyricsBadge = localBadges->findChild<QObject *>("localLyricsBadge");
  auto *artworkBadge = localBadges->findChild<QObject *>("localArtworkBadge");
  if (!lyricsBadge || !artworkBadge || !lyricsBadge->property("visible").toBool() || !artworkBadge->property("visible").toBool()) return 1;
  localBadges->setProperty("showLocalExtras", false);
  if (lyricsBadge->property("visible").toBool() || artworkBadge->property("visible").toBool()) return 1;
  playlistArtwork->setProperty("media", QVariantMap{{"id", "local-playlist:test"}, {"source", "local"}});
  playlistArtwork->setProperty("customArtwork", QString());
  const QString generatedBackground = playlistArtwork->property("backgroundArtwork").toString();
  QImage backgroundImage;
  if (!generatedBackground.startsWith(QStringLiteral("data:image/svg+xml;base64,"))
      || !backgroundImage.loadFromData(QByteArray::fromBase64(generatedBackground.section(',', 1).toLatin1()))
      || backgroundImage.pixelColor(256, 10) == backgroundImage.pixelColor(256, 500)) {
    output << "Generated playlist artwork must supply a decodable gradient image for its background.\n";
    return 1;
  }
  QQmlContext deviceLibraryContext(engine.rootContext());
  deviceLibraryContext.setContextProperty("NumaSync", QVariantMap{{"revision", -1}});
  deviceLibraryContext.setContextProperty("NumaHome", QVariantMap{{"sections", QVariantList{QVariantMap{
    {"title", "Library"}, {"items", QVariantList{QVariantMap{{"id", "LM"}, {"type", "playlist"}},
      QVariantMap{{"id", "local-playlist:other-device"}, {"type", "playlist"}}}}}}}});
  QTemporaryDir deviceLibraryRoot;
  LocalLibrary deviceLibrary(deviceLibraryRoot.path());
  const QString devicePlaylistId = deviceLibrary.createPlaylist(QStringLiteral("Hika"));
  deviceLibraryContext.setContextProperty("NumaLocal", &deviceLibrary);
  QQmlComponent homeLibraryComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/views/home/HomeView.qml")));
  std::unique_ptr<QObject> homeLibrary(homeLibraryComponent.create(&deviceLibraryContext));
  if (!homeLibrary) return 1;
  const auto libraryFeed = homeLibrary->property("feed").value<QJSValue>().toVariant().toList();
  if (libraryFeed.size() != 1) { output << "Device playlists must join the existing Library shelf, not create a second shelf at the bottom.\n"; return 1; }
  const auto libraryItems = libraryFeed.first().toMap().value("items").toList();
  if (libraryItems.size() != 2 || libraryItems.last().toMap().value("id") != devicePlaylistId) {
    output << "Home library must include device-local playlists and exclude other devices' local lists.\n";
    return 1;
  }
  QQmlComponent volumeModelComponent(&engine);
  volumeModelComponent.setData("import QtQuick\nQtObject { property real volume: 0.37 }", QUrl());
  std::unique_ptr<QObject> volumeModel(volumeModelComponent.create());
  QQmlContext playerContext(engine.rootContext());
  playerContext.setContextProperty("NumaHome", volumeModel.get());
  auto playerAppearance = engine.rootContext()->contextProperty("NumaAppearance").toMap();
  playerAppearance.insert("showBitrate", false);
  playerContext.setContextProperty("NumaAppearance", playerAppearance);
  QVariantMap localTrack{{"id", "local:format-test"}, {"title", "Local song"}, {"artist", "Artist"}, {"codec", "flac"}};
  auto playerState = engine.rootContext()->contextProperty("NumaPlayback").toMap();
  playerState.insert("track", localTrack);
  playerState.insert("transitionTrack", QVariantMap{});
  playerState.insert("bitrate", 757000);
  playerState.insert("playing", false);
  playerState.insert("loading", false);
  playerState.insert("queue", QVariantList{});
  playerState.insert("repeatMode", "off");
  playerState.insert("shuffleEnabled", false);
  playerContext.setContextProperty("NumaPlayback", playerState);
  QQmlComponent playerTestComponent(&engine);
  playerTestComponent.setData("import QtQuick\nimport Numa\nItem { id: stage; width: 1100; height: 70; PlayerBar { objectName: 'volumeTestBar'; anchors.fill: parent; backdrop: stage } }", QUrl());
  std::unique_ptr<QObject> playerTest(playerTestComponent.create(&playerContext));
  if (!playerTest) return 1;
  auto *testBar = playerTest->findChild<QObject *>("volumeTestBar");
  if (!testBar) return 1;
  QMetaObject::invokeMethod(testBar, "toggleMute");
  if (volumeModel->property("volume").toDouble() != 0) return 1;
  QMetaObject::invokeMethod(testBar, "toggleMute");
  if (qAbs(volumeModel->property("volume").toDouble() - 0.37) > 0.0001) return 1;
  QVariant localFormat;
  QMetaObject::invokeMethod(testBar, "localLabel", Q_RETURN_ARG(QVariant, localFormat), Q_ARG(QVariant, QVariant(localTrack)));
  if (localFormat.toString() != QStringLiteral("FLAC")) {
    output << "Local bitrates must respect the bitrate setting.\n";
    return 1;
  }
  playerAppearance.insert("showBitrate", true);
  playerContext.setContextProperty("NumaAppearance", playerAppearance);
  QMetaObject::invokeMethod(testBar, "localLabel", Q_RETURN_ARG(QVariant, localFormat), Q_ARG(QVariant, QVariant(localTrack)));
  if (!localFormat.toString().contains(QStringLiteral("757 kbps"))) return 1;
  for (const QString theme : {QStringLiteral("rose_pine"), QStringLiteral("rose_pine_dawn")}) {
    const bool dawn = theme.endsWith("dawn");
    QQmlContext bloomContext(engine.rootContext());
    bloomContext.setContextProperty("NumaAppearance", QVariantMap{{"theme", theme}, {"rosePine", true},
      {"textColor", dawn ? "#575279" : "#e0def4"}, {"baseColor", dawn ? "#faf4ed" : "#191724"}});
    QQmlComponent bloomComponent(&engine);
    bloomComponent.setData(R"(
import QtQuick
import QtQuick.Controls
import Numa
ApplicationWindow {
    id: preview
    width: 280; height: 120
    color: NumaAppearance.baseColor
    property color paint: NumaAppearance.theme === "rose_pine_dawn" ? "#efd4cf" : "#ebbcba"
    property bool glowHovered: true
    Button {
        id: pinkButton
        HandCursor {}
        x: 50; y: 40; width: 180; height: 40
        background: Rectangle { radius: 10; color: preview.paint }
        contentItem: Text { text: "Buscar actualizaciones"; color: "#575279"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
        HoverTint { objectName: "testBloom"; control: pinkButton; hovered: preview.glowHovered }
    }
}
)", QUrl());
    std::unique_ptr<QObject> bloomPreview(bloomComponent.create(&bloomContext));
    if (!bloomPreview) {
      for (const auto &error : bloomComponent.errors()) output << error.toString() << '\n';
      return 1;
    }
    auto *halo = bloomPreview->findChild<QObject *>("testBloom");
    if (!halo || !halo->property("active").toBool()) return 1;
    for (const auto &excluded : {"#fffaf3", "#eb6f92", "#40323e", "#d7827e"}) {
      bloomPreview->setProperty("paint", QColor(excluded));
      if (halo->property("active").toBool()) {
        output << "Bloom must remain off for other button colors: " << excluded << '\n';
        return 1;
      }
    }
    bloomPreview->setProperty("paint", QColor(dawn ? "#efd4cf" : "#ebbcba"));
    bloomPreview->setProperty("glowHovered", false);
    if (halo->property("active").toBool()) return 1;
    bloomPreview->setProperty("glowHovered", true);
    auto *bloomWindow = qobject_cast<QQuickWindow *>(bloomPreview.get());
    bloomWindow->show();
    QElapsedTimer renderWait; renderWait.start();
    while (renderWait.elapsed() < 260) { application.processEvents(); QThread::msleep(5); }
    QMouseEvent hoverMove(QEvent::MouseMove, QPointF(100, 60), QPointF(bloomWindow->mapToGlobal(QPoint(100, 60))), Qt::NoButton, Qt::NoButton, Qt::NoModifier);
    QCoreApplication::sendEvent(bloomWindow, &hoverMove);
    application.processEvents();
    auto *createdHalo = bloomPreview->findChild<QObject *>("buttonHoverTint");
    if (!createdHalo || !createdHalo->property("active").toBool()) {
      output << "Hovering a pink button must create its shared hover tint automatically.\n";
      return 1;
    }
    bloomWindow->close();
  }
  output << "Numa QML startup check passed.\n";
  return 0;
}
