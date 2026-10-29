#include "account/cloud_cover_crop.h"
#include <QGuiApplication>
#include <QFontDatabase>
#include <QFont>
#include <QQuickWindow>
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
      {"theme", "crystal"}, {"rosePine", false}, {"performanceMode", false}, {"borderColor", "#1cffffff"}, {"borderWidth", 1}, {"controlBorderWidth", 1}, {"bordersEnabled", true},
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
  for (bool rose : {false, true}) {
    engine.rootContext()->setContextProperty(QStringLiteral("NumaAppearance"), QVariantMap{
        {"theme", rose ? "rose_pine" : "crystal"}, {"rosePine", rose}, {"performanceMode", rose},
        {"borderColor", rose ? "#ebbcba" : "#1cffffff"}, {"borderWidth", rose ? 2 : 1},
        {"controlBorderWidth", rose ? 0 : 1},
        {"bordersEnabled", true},
        {"textColor", rose ? "#e0def4" : "#f5f3ee"}, {"mutedColor", "#908caa"}, {"accentColor", "#c4a7e7"}, {"surfaceColor", "#1f1d2e"}});
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
  for (bool expected : {true, false}) {
    QMetaObject::invokeMethod(libraryButton, "clicked");
    if (sync->property("saved").toBool() != expected || libraryButton->property("glyph").toString() != (expected ? "check" : "library-big")) return 1;
  }
  if (libraryButton->property("width") != artworkButton->property("width")
      || libraryButton->property("glyphColor").value<QColor>() != QColor("#f7f5f0")
      || artworkButton->property("glyphColor").value<QColor>() != QColor("#f7f5f0")) {
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
  output << "Numa QML startup check passed.\n";
  return 0;
}
